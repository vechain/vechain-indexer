"""SNS → Slack bridge for AMP Alertmanager and CloudWatch alarms.

Alertmanager pre-renders its Slack body, so that path is forwarded verbatim.
CloudWatch publishes JSON, which is rendered here into the same shape. Alerts
labelled live_only are dropped unless their colour holds the live Route53
record for their network. See terraform/observability/README.md.
"""

import json
import os
import time
import urllib.error
import urllib.request

import boto3
from botocore.exceptions import ClientError

_secrets_client = boto3.client("secretsmanager")
_route53_client = boto3.client("route53")
_webhook_cache: dict[str, float | str | None] = {"url": None, "fetched_at": 0.0}
_live_colour_cache: dict[str, tuple[float, str | None]] = {}
_CACHE_TTL_SECONDS = 300
_LIVE_COLOUR_TTL_SECONDS = 60
_PLACEHOLDER_SENTINEL = "placeholder"
_STATE_SUFFIX = {"OK": " — resolved", "INSUFFICIENT_DATA": " — insufficient data"}


def _render_cloudwatch_alarm(message: str) -> str | None:
    """Render a CloudWatch alarm to match the Alertmanager template, or None if not one.

    terraform/api/alarms.tf writes descriptions as "<header> — <summary>"; the header
    already carries the "[env/deployment/network] service: Title" prefix Alertmanager
    builds from .CommonLabels, which a CloudWatch payload has no labels to build.
    """
    try:
        payload = json.loads(message)
    except (ValueError, TypeError):
        return None
    if not isinstance(payload, dict) or "AlarmName" not in payload:
        return None

    description = (payload.get("AlarmDescription") or payload["AlarmName"]).strip()
    header, _, summary = description.partition(" — ")
    rendered = f"*{header.strip()}*{_STATE_SUFFIX.get(payload.get('NewStateValue', ''), '')}"
    return f"{rendered}\n{summary.strip()}" if summary.strip() else rendered


def _resolve_webhook_url() -> str | None:
    # Cache both real URLs and placeholder results so we don't hammer SM
    # while the webhook is unset. On transient SM errors fall back to the
    # last cached value — alerting reliability outweighs catching a
    # freshly-rotated URL during a blip. `fetched_at > 0` is the "populated"
    # marker.
    now = time.time()
    fetched_at = float(_webhook_cache.get("fetched_at") or 0.0)
    if fetched_at > 0.0 and now - fetched_at < _CACHE_TTL_SECONDS:
        cached = _webhook_cache.get("url")
        return str(cached) if cached else None

    secret_arn = os.environ["SLACK_WEBHOOK_SECRET_ARN"]
    try:
        resp = _secrets_client.get_secret_value(SecretId=secret_arn)
    except ClientError as exc:
        if fetched_at > 0.0:
            cached = _webhook_cache.get("url")
            print(f"slack webhook secret read failed; using cached value: {exc}")
            return str(cached) if cached else None
        print(f"slack webhook secret read failed: {exc}")
        return None

    value = (resp.get("SecretString") or "").strip()
    url: str | None = value if (value and value != _PLACEHOLDER_SENTINEL) else None
    _webhook_cache["url"] = url
    _webhook_cache["fetched_at"] = now
    return url


def _live_colour(network: str) -> str | None:
    """Colour named by the live Route53 record for `network`, or None if unknown."""
    now = time.time()
    cached = _live_colour_cache.get(network)
    if cached and now - cached[0] < _LIVE_COLOUR_TTL_SECONDS:
        return cached[1]

    name = os.environ["LIVE_RECORD_TEMPLATE"].format(network=network).rstrip(".") + "."
    try:
        resp = _route53_client.list_resource_record_sets(
            HostedZoneId=os.environ["LIVE_ZONE_ID"],
            StartRecordName=name,
            StartRecordType="CNAME",
            MaxItems="1",
        )
    except ClientError as exc:
        print(f"live colour lookup failed for {network}: {exc}")
        return None

    colour = None
    for record_set in resp.get("ResourceRecordSets", []):
        if record_set.get("Name") != name:
            continue
        value = (record_set.get("ResourceRecords") or [{}])[0].get("Value", "")
        colour = value.split("-")[1] if value.startswith("prod-") else None
    _live_colour_cache[network] = (now, colour)
    return colour


def _suppressed_for_dead_colour(attributes: dict) -> bool:
    # Fail open: an unknown live colour or a missing attribute forwards the alert.
    values = {k: (v or {}).get("Value") for k, v in attributes.items()}
    if values.get("live_only") != "true":
        return False
    deployment, network = values.get("deployment"), values.get("network")
    if not deployment or not network:
        return False
    live = _live_colour(network)
    return live is not None and deployment != live


def _post_to_slack(webhook_url: str, text: str) -> None:
    # Failures re-raise so SNS retries with backoff — duplicate Slack
    # messages are preferable to dropped alerts.
    body = json.dumps({"text": text}).encode("utf-8")
    req = urllib.request.Request(
        webhook_url,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        urllib.request.urlopen(req, timeout=5).close()
    except urllib.error.HTTPError as exc:
        print(f"slack POST returned {exc.code}: {exc.read().decode('utf-8', 'replace')}")
        raise
    except urllib.error.URLError as exc:
        print(f"slack POST failed: {exc}")
        raise


def handler(event: dict, _context) -> None:
    webhook_url = _resolve_webhook_url()
    if not webhook_url:
        print("slack webhook URL not configured (placeholder); skipping delivery")
        return

    for record in event.get("Records", []):
        sns = record.get("Sns") or {}
        text = sns.get("Message", "").strip()
        if not text:
            print("SNS record missing Message; skipping")
            continue
        if _suppressed_for_dead_colour(sns.get("MessageAttributes") or {}):
            print(f"dropping live-only alert for a dead colour: {text.splitlines()[0]}")
            continue
        _post_to_slack(webhook_url, _render_cloudwatch_alarm(text) or text)
