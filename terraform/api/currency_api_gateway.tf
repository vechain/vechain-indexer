variable "domain_name_data" {
  default = {
    "dev" = {
      "name" = "coin-api.dev.veworld.vechain.org"
      "zone" = "Z05174081HJUXVROTDH6I"
    },
    "prod" = {
      "name" = "coin-api.veworld.vechain.org"
      "zone" = "Z07511592AUMA3GPYN856"
    },
    "prod-blue" = {
      "name" = "coin-api.veworld.vechain.org"
      "zone" = "Z07511592AUMA3GPYN856"
    },
    "prod-green" = {
      "name" = "coin-api.veworld.vechain.org"
      "zone" = "Z07511592AUMA3GPYN856"
    },
  }
}

locals {
  // following endpoints have to be proxied / cached:
  // https://api.coingecko.com/api/v3/coins/vechain/market_chart?days=7&interval=daily&vs_currency=USD
  // https://api.coingecko.com/api/v3/coins/list?include_platform=true&days=1
  // https://api.coingecko.com/api/v3/coins/vechain
  // https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=1&sparkline=false&price_change_percentage=1h%2C24h%2C7d
  // https://api.coingecko.com/api/v3/simple/supported_vs_currencies
  // implement this as rest api gateway that sits on top of coin-api.dev.veworld | coin-api.prod.veworld
  api_domain = var.domain_name_data[terraform.workspace]
  cors       = {
    "responses" : {
      "200" : {
        "description" : "200 response",
        "headers" : {
          "Access-Control-Allow-Headers" : {
            "schema" : {
              "type" : "string"
            }
          },
          "Access-Control-Allow-Methods" : {
            "schema" : {
              "type" : "string"
            }
          },
          "Access-Control-Allow-Origin" : {
            "schema" : {
              "type" : "string"
            }
          }
        },
        "content" : {
          "application/json" : {
            "schema" : {
              "$ref" : "#/components/schemas/Empty"
            }
          }
        }
      }
    },
    "x-amazon-apigateway-integration" : {
      "responses" : {
        "default" : {
          "statusCode" : "200",
          "responseParameters" : {
            "method.response.header.Access-Control-Allow-Headers" : "'Content-Type,Authorization,X-Amz-Date,X-Api-Key,X-Amz-Security-Token'",
            "method.response.header.Access-Control-Allow-Methods" : "'GET,HEAD,OPTIONS'",
            "method.response.header.Access-Control-Allow-Origin" : "'*'"
          }
        }
      },
      "passthroughBehavior" : "when_no_match",
      "requestTemplates" : {
        "application/json" : "{\"statusCode\": 200}"
      },
      "type" : "MOCK"
    }
  }
  api_integration_model = {
    //openapi specification for api gateway
    "openapi" : "3.0.1",
    "info" : {
      "title" : "Coin Currency Cache",
      "version" : "1.0"
    },
    "components" : {
      "schemas" : {
        "Empty" : {
          "type" : "object",
          "title" : "Empty Schema"
        }
      }
    },
    "paths" : {
      //add cors to all paths
      "/simple" : {
        "options" : local.cors
      },
      "/simple/supported_vs_currencies" : {
        "options" : local.cors,
        "get" : {
          "responses" : {
            "200" : {
              "description" : "200 response",
              "content" : {
                "application/json" : {
                  "schema" : {
                    "$ref" : "#/components/schemas/Empty"
                  }
                }
              }
            }
          },
          "x-amazon-apigateway-integration" : {
            "responses" : {
              "default" : {
                "statusCode" : "200"
              }
            },
            "passthroughBehavior" : "when_no_match",
            "type" : "HTTP_PROXY",
            "httpMethod" : "GET",
            "uri" : "https://api.coingecko.com/api/v3/simple/supported_vs_currencies",
          }
        }
      },
      "/coins" : {
        "options" : local.cors
      },
      "/coins/{coin_id}" : {
        "options" : local.cors,
        "get" : {
          // request parameters
          "parameters" : [
            {
              "name" : "coin_id",
              "in" : "path",
              "required" : true,
              "schema" : {
                "type" : "string"
              }
            }
          ],
          "responses" : {
            "200" : {
              "description" : "200 response",
              "content" : {
                "application/json" : {
                  "schema" : {
                    "$ref" : "#/components/schemas/Empty"
                  }
                }
              }
            }
          },
          "x-amazon-apigateway-integration" : {
            "responses" : {
              "default" : {
                "statusCode" : "200"
              }
            },
            "passthroughBehavior" : "when_no_match",
            "type" : "HTTP_PROXY",
            "httpMethod" : "GET",
            "uri" : "https://api.coingecko.com/api/v3/coins/{coin_id}",
            "requestParameters" : {
              "integration.request.path.coin_id" : "method.request.path.coin_id"
            },
            "cacheKeyParameters" : [
              "method.request.path.coin_id"
            ],

          }
        }
      },
      "/coins/{coin_id}/market_chart" : {
        "options" : local.cors
        "get" : {
          // request parameters
          "parameters" : [
            {
              "name" : "coin_id",
              "in" : "path",
              "required" : true,
              "schema" : {
                "type" : "string"
              }
            },
            {
              "name" : "days",
              "in" : "query",
              "required" : true,
              "schema" : {
                "type" : "integer"
              }
            },
            {
              "name" : "interval",
              "in" : "query",
              "required" : false,
              "schema" : {
                "type" : "string"
              }
            },
            {
              "name" : "vs_currency",
              "in" : "query",
              "required" : true,
              "schema" : {
                "type" : "string"
              }
            }
          ],
          "responses" : {
            "200" : {
              "description" : "200 response",
              "content" : {
                "application/json" : {
                  "schema" : {
                    "$ref" : "#/components/schemas/Empty"
                  }
                }
              }
            }
          },
          "x-amazon-apigateway-integration" : {
            "responses" : {
              "default" : {
                "statusCode" : "200"
              }
            },
            "passthroughBehavior" : "when_no_match",
            "type" : "HTTP_PROXY",
            "httpMethod" : "GET",
            "uri" : "https://api.coingecko.com/api/v3/coins/{coin_id}/market_chart",
            "requestParameters" : {
              "integration.request.path.coin_id" : "method.request.path.coin_id",
              "integration.request.querystring.days" : "method.request.querystring.days",
              "integration.request.querystring.interval" : "method.request.querystring.interval",
              "integration.request.querystring.vs_currency" : "method.request.querystring.vs_currency"
            },
            "cacheKeyParameters" : [
              "method.request.path.coin_id",
              "method.request.querystring.days",
              "method.request.querystring.interval",
              "method.request.querystring.vs_currency"
            ],

          }
        }
      },
      "/coins/markets" : {
        "options" : local.cors,
        "get" : {
          // request parameters
          "parameters" : [
            {
              "name" : "vs_currency",
              "in" : "query",
              "required" : true,
              "schema" : {
                "type" : "string"
              }
            },
            {
              "name" : "order",
              "in" : "query",
              "required" : false,
              "schema" : {
                "type" : "string"
              }
            },
            {
              "name" : "per_page",
              "in" : "query",
              "required" : false,
              "schema" : {
                "type" : "integer"
              }
            },
            {
              "name" : "page",
              "in" : "query",
              "required" : false,
              "schema" : {
                "type" : "integer"
              }
            },
            {
              "name" : "sparkline",
              "in" : "query",
              "required" : false,
              "schema" : {
                "type" : "boolean"
              }
            },
            {
              "name" : "price_change_percentage",
              "in" : "query",
              "required" : false,
              "schema" : {
                "type" : "string"
              }
            }
          ],
          "responses" : {
            "200" : {
              "description" : "200 response",
              "content" : {
                "application/json" : {
                  "schema" : {
                    "$ref" : "#/components/schemas/Empty"
                  }
                }
              }
            }
          },
          "x-amazon-apigateway-integration" : {
            "responses" : {
              "default" : {
                "statusCode" : "200"
              }
            },
            "passthroughBehavior" : "when_no_match",
            "type" : "HTTP_PROXY",
            "httpMethod" : "GET",
            "uri" : "https://api.coingecko.com/api/v3/coins/markets",
            "requestParameters" : {
              "integration.request.querystring.vs_currency" : "method.request.querystring.vs_currency",
              "integration.request.querystring.order" : "method.request.querystring.order",
              "integration.request.querystring.per_page" : "method.request.querystring.per_page",
              "integration.request.querystring.page" : "method.request.querystring.page",
              "integration.request.querystring.sparkline" : "method.request.querystring.sparkline",
              "integration.request.querystring.price_change_percentage" : "method.request.querystring.price_change_percentage",
            },
            "cacheKeyParameters" : [
              "method.request.querystring.vs_currency",
              "method.request.querystring.order",
              "method.request.querystring.per_page",
              "method.request.querystring.page",
              "method.request.querystring.sparkline",
              "method.request.querystring.price_change_percentage",
            ],
          }
        }
      },
      "/coins/list" : {
        "options" : local.cors,
        "get" : {
          //request parameters
          "parameters" : [
            {
              "name" : "include_platform",
              "in" : "query",
              "required" : false,
              "schema" : {
                "type" : "boolean"
              }
            }
          ],
          "responses" : {
            "200" : {
              "description" : "200 response",
              "content" : {
                "application/json" : {
                  "schema" : {
                    "$ref" : "#/components/schemas/Empty"
                  }
                }
              }
            }
          },
          "x-amazon-apigateway-integration" : {
            "responses" : {
              "default" : {
                "statusCode" : "200"
              }
            },
            "passthroughBehavior" : "when_no_match",
            "type" : "HTTP_PROXY",
            "httpMethod" : "GET",
            "uri" : "https://api.coingecko.com/api/v3/coins/list",
            "requestParameters" : {
              "integration.request.querystring.include_platform" : "method.request.querystring.include_platform",
            },
            "cacheKeyParameters" : [
              "method.request.querystring.include_platform",
            ],
          }
        }
      }
    },
  }

  api_integration_json = jsonencode(local.api_integration_model)
}

resource "aws_acm_certificate" "domain_cert" {
  domain_name       = local.api_domain.name
  validation_method = "DNS"
  provider          = aws.us_east_1
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "domain_cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.domain_cert.domain_validation_options : dvo.domain_name => {
      name  = dvo.resource_record_name
      type  = dvo.resource_record_type
      value = dvo.resource_record_value
    }
  }

  name    = each.value.name
  type    = each.value.type
  zone_id = local.api_domain.zone
  records = [each.value.value]
  ttl     = 60
}

resource "aws_acm_certificate_validation" "cert_validation_block" {
  certificate_arn         = aws_acm_certificate.domain_cert.arn
  provider                = aws.us_east_1
  validation_record_fqdns = [
    for record in aws_route53_record.domain_cert_validation : record.fqdn
  ]
}

resource "aws_api_gateway_rest_api" "currency_cache" {
  name              = "coin_currency_cache"
  fail_on_warnings  = true
  body              = local.api_integration_json
  put_rest_api_mode = "merge"
  endpoint_configuration {
    types = ["EDGE"]
  }
}
// settings for all resources
resource "aws_api_gateway_method_settings" "all" {
  method_path = "*/*"
  rest_api_id = aws_api_gateway_rest_api.currency_cache.id
  stage_name  = aws_api_gateway_stage.default.stage_name
  settings {
    logging_level          = "ERROR"
    metrics_enabled        = true
    data_trace_enabled     = true
    throttling_burst_limit = 5000
    throttling_rate_limit  = 10000
    caching_enabled        = true
    cache_ttl_in_seconds   = 30
  }
}


resource "aws_api_gateway_domain_name" "api" {
  domain_name     = local.api_domain.name
  certificate_arn = aws_acm_certificate_validation.cert_validation_block.certificate_arn
  security_policy = "TLS_1_2"

  endpoint_configuration {
    types = ["EDGE"]
  }
}

resource "aws_route53_record" "apigw_domain_alias" {
  name    = local.api_domain.name
  type    = "A"
  zone_id = local.api_domain.zone

  alias {
    name                   = aws_api_gateway_domain_name.api.cloudfront_domain_name
    zone_id                = aws_api_gateway_domain_name.api.cloudfront_zone_id
    evaluate_target_health = false
  }
}

resource "aws_api_gateway_base_path_mapping" "currency_cache" {
  api_id      = aws_api_gateway_rest_api.currency_cache.id
  domain_name = aws_api_gateway_domain_name.api.domain_name
  stage_name  = aws_api_gateway_stage.default.stage_name
}

resource "aws_api_gateway_deployment" "deployment" {
  rest_api_id = aws_api_gateway_rest_api.currency_cache.id
  triggers    = {
    redeployment = sha1(local.api_integration_json)
  }
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_cloudwatch_log_group" "gw_log_group" {
  name_prefix = "api_gw_log_group"
  retention_in_days = 30
}

resource "aws_api_gateway_stage" "default" {
  deployment_id = aws_api_gateway_deployment.deployment.id
  rest_api_id   = aws_api_gateway_rest_api.currency_cache.id
  stage_name    = "default"
  cache_cluster_enabled = true
  cache_cluster_size = "0.5"
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.gw_log_group.arn
    format          = "$context.status,$context.identity.sourceIp,$context.requestTime,$context.httpMethod,$context.protocol,$context.responseLength,$context.requestId,\"$context.resourcePath\",$context.integration.integrationStatus,$context.integration.status,\"$context.error.message\",\"$context.integrationErrorMessage\"\n"
  }
}

resource "aws_api_gateway_account" "gw_role_account" {
  cloudwatch_role_arn = aws_iam_role.gw_cloudwatch.arn
}

data "aws_iam_policy_document" "gw_assume_role" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["apigateway.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "gw_cloudwatch" {
  name_prefix        = "api_gateway_cloudwatch_global"
  assume_role_policy = data.aws_iam_policy_document.gw_assume_role.json
}

data "aws_iam_policy_document" "gw_cloudwatch" {
  statement {
    effect = "Allow"

    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:DescribeLogGroups",
      "logs:DescribeLogStreams",
      "logs:PutLogEvents",
      "logs:GetLogEvents",
      "logs:FilterLogEvents",
    ]

    resources = ["*"]
  }
}
resource "aws_iam_role_policy" "gw_cloudwatch" {
  name   = "default"
  role   = aws_iam_role.gw_cloudwatch.id
  policy = data.aws_iam_policy_document.gw_cloudwatch.json
}
