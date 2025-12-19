# Datadog Dashboards

## Overview

This directory contains Datadog dashboard JSON files that can be imported directly into Datadog.

## Available Dashboards

| Dashboard               | Description                                            |
|-------------------------|--------------------------------------------------------|
| `mongodb-metrics.json`  | MongoDB driver and Spring Data repository metrics      |
| `indexing-metrics.json` | Indexer processing metrics (events, blocks, durations) |
| `client-metrics.json`   | Thor client HTTP request/response metrics              |
| `jvm-metrics.json`      | JVM memory, threads, garbage collection metrics        |

## How These Dashboards Were Generated

These dashboards were generated using **Claude Code** by converting the existing Grafana dashboards located in
`metrics/grafana/provisioning/dashboards/`.

### Process

1. **Reference Grafana Dashboards**: Each Grafana dashboard JSON was used as the source of truth for widget layouts,
   queries, and visualizations.

2. **Metric Name Verification**: The actual metric names being sent to Datadog were verified against
   `sent-metrics.json` (a capture of metrics sent to the Datadog API). This was critical because:
    - Prometheus metrics use underscores (e.g., `jvm_memory_used_bytes`)
    - Datadog metrics from Micrometer use dots (e.g., `jvm.memory.used`)
    - Some metrics have different suffixes (e.g., `.sum`, `.count`, `.avg`, `.max`)

   **To regenerate `sent-metrics.json`:**
    1. Run the application locally
    2. Set a breakpoint in `io.micrometer.datadog.DatadogMeterRegistry.publish()`
    3. Copy the `body` variable contents
    4. Paste into a new `sent-metrics.json` file

3. **Query Translation**: Prometheus/PromQL queries were translated to Datadog query syntax:
    - `rate(metric[$__rate_interval])` → `metric{*}.as_rate()`
    - `sum by(label)` → `sum:metric{*} by {label}`
    - `histogram_quantile(0.95, ...)` → `p95:metric{*}` (where available)

4. **Widget Type Mapping**:
    - Grafana `stat` → Datadog `query_value`
    - Grafana `timeseries` → Datadog `timeseries`
    - Grafana `bargauge` → Datadog `toplist`
    - Grafana `table` → Datadog `toplist` (simplified)

### Key Learnings

- Datadog timeseries widgets require `formulas` before `queries` in the request object
- Use `"order_by": "values"` in style for timeseries (not `"palette": "dog_classic"`)
- Timer metrics in Datadog have `.sum`, `.count`, `.avg`, `.max` suffixes
- Counter metrics can use `.as_rate()` for per-second rates

## Importing Dashboards

1. Go to Datadog → Dashboards → New Dashboard
2. Click the gear icon → Import dashboard JSON
3. Paste the contents of any JSON file from this directory
4. Click Import