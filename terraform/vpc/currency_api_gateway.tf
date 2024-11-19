variable "domain_name_data" {
  default = {
    "dev" = {
      "name" = "coin-api.dev.veworld.vechain.org"
      "zone" = "Z05174081HJUXVROTDH6I"
    },
    "prod" = {
      "name" = "coin-api.veworld.vechain.org"
      "zone" = "Z07511592AUMA3GPYN856"
    }
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

  security_group_ids = {
    "dev"  = aws_security_group.sg_dev.id
    "prod" = aws_security_group.sg_prod.id
    "prod-blue" = aws_security_group.sg_prod_blue.id
    "prod-green" = aws_security_group.sg_prod_green.id
  }
}

resource "aws_acm_certificate" "domain_cert" {
  count = local.env.environment == "prod" ? 1 : 0
  domain_name       = local.api_domain.name
  validation_method = "DNS"
  provider          = aws.us_east_1
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "domain_cert_validation" {
  for_each = can(aws_acm_certificate.domain_cert[0]) ? {
    for dvo in aws_acm_certificate.domain_cert[0].domain_validation_options :
    dvo.domain_name => {
      name  = dvo.resource_record_name
      type  = dvo.resource_record_type
      value = dvo.resource_record_value
    }
  } : {}

  name    = each.value.name
  type    = each.value.type
  zone_id = local.api_domain.zone
  records = [each.value.value]
  ttl     = 60
}

resource "aws_acm_certificate_validation" "cert_validation_block" {
  count = local.env.environment == "prod" ? 1 : 0
  certificate_arn         = aws_acm_certificate.domain_cert[0].arn
  provider                = aws.us_east_1
  validation_record_fqdns = [
    for record in aws_route53_record.domain_cert_validation : record.fqdn
  ]
}

resource "aws_api_gateway_rest_api" "currency_cache" {
  count = local.env.environment == "prod" ? 1 : 0
  name              = "coin_currency_cache"
  body              = local.api_integration_json
  put_rest_api_mode = "merge"
  endpoint_configuration {
    types = ["EDGE"]
  }

  lifecycle {
    create_before_destroy = true
  }
}
// settings for all resources
resource "aws_api_gateway_method_settings" "all" {
  count = local.env.environment == "prod" ? 1 : 0
  method_path = "*/*"
  rest_api_id = aws_api_gateway_rest_api.currency_cache[0].id
  stage_name  = aws_api_gateway_stage.default[0].stage_name
  settings {
    logging_level          = "ERROR"
    metrics_enabled        = true
    data_trace_enabled     = false
    throttling_burst_limit = 5000
    throttling_rate_limit  = 10000
    caching_enabled        = true
    cache_ttl_in_seconds   = 30
    cache_data_encrypted   = true
  }
}


resource "aws_api_gateway_domain_name" "api" {
  count = local.env.environment == "prod" ? 1 : 0
  domain_name     = local.api_domain.name
  certificate_arn = aws_acm_certificate_validation.cert_validation_block[0].certificate_arn
  security_policy = "TLS_1_2"

  endpoint_configuration {
    types = ["EDGE"]
  }
}

resource "aws_route53_record" "apigw_domain_alias" {
  count = local.env.environment == "prod" ? 1 : 0
  name    = local.api_domain.name
  type    = "A"
  zone_id = local.api_domain.zone

  alias {
    name                   = aws_api_gateway_domain_name.api[0].cloudfront_domain_name
    zone_id                = aws_api_gateway_domain_name.api[0].cloudfront_zone_id
    evaluate_target_health = false
  }
}

resource "aws_api_gateway_base_path_mapping" "currency_cache" {
  count = local.env.environment == "prod" ? 1 : 0
  api_id      = aws_api_gateway_rest_api.currency_cache[0].id
  domain_name = aws_api_gateway_domain_name.api[0].domain_name
  stage_name  = aws_api_gateway_stage.default[0].stage_name
}

resource "aws_api_gateway_deployment" "deployment" {
  count = local.env.environment == "prod" ? 1 : 0
  rest_api_id = aws_api_gateway_rest_api.currency_cache[0].id
  triggers    = {
    redeployment = sha1(local.api_integration_json)
  }
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_cloudwatch_log_group" "gw_log_group" {
  count = local.env.environment == "prod" ? 1 : 0
  name_prefix = "api_gw_log_group"
  retention_in_days = 30
}

resource "aws_api_gateway_client_certificate" "client_cert" {
  count = local.env.environment == "prod" ? 1 : 0
  description = "Client certificate for API Gateway"
}

resource "aws_api_gateway_stage" "default" {
  count = local.env.environment == "prod" ? 1 : 0
  deployment_id = aws_api_gateway_deployment.deployment[0].id
  rest_api_id   = aws_api_gateway_rest_api.currency_cache[0].id
  stage_name    = "default"
  cache_cluster_enabled = true
  cache_cluster_size = "0.5"
  xray_tracing_enabled = true
  client_certificate_id = aws_api_gateway_client_certificate.client_cert[0].id
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.gw_log_group[0].arn
    format          = "$context.status,$context.identity.sourceIp,$context.requestTime,$context.httpMethod,$context.protocol,$context.responseLength,$context.requestId,\"$context.resourcePath\",$context.integration.integrationStatus,$context.integration.status,\"$context.error.message\",\"$context.integrationErrorMessage\"\n"
  }
}

resource "aws_api_gateway_account" "gw_role_account" {
  count = local.env.environment == "prod" ? 1 : 0
  cloudwatch_role_arn = aws_iam_role.gw_cloudwatch[0].arn
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
  count = local.env.environment == "prod" ? 1 : 0
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
  count = local.env.environment == "prod" ? 1 : 0
  name   = "default"
  role   = aws_iam_role.gw_cloudwatch[0].id
  policy = data.aws_iam_policy_document.gw_cloudwatch.json
}

resource "aws_kms_key" "lambda_env_var_encryption" {
  description         = "KMS key for encrypting Lambda environment variables"
  enable_key_rotation = true
}

resource "aws_sqs_queue" "dlq" {
  name = "lambda_dlq"
  kms_master_key_id = aws_kms_key.lambda_env_var_encryption.arn
}

resource "aws_iam_policy" "lambda_sqs_policy" {
  name        = "LambdaSQSPolicy"
  description = "Policy for Lambda to send messages to SQS"
  policy      = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Action = [
          "sqs:SendMessage"
        ],
        Resource = aws_sqs_queue.dlq.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_exec_policy_attachment" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = aws_iam_policy.lambda_sqs_policy.arn
}

resource "aws_iam_policy" "lambda_vpc_policy" {
  name        = "LambdaVPCPolicy"
  description = "Policy for Lambda to manage network interfaces in VPC"
  policy      = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Action = [
          "ec2:CreateNetworkInterface",
          "ec2:DescribeNetworkInterfaces",
          "ec2:DeleteNetworkInterface",
          "ec2:AttachNetworkInterface"
        ],
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_exec_policy_vpc" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = aws_iam_policy.lambda_vpc_policy.arn
}

data "archive_file" "lambda_zip" {
  type        = "zip"
  source_file = "./coingecko-proxy/lambda.js"
  output_path = "./coingecko-proxy/lambda.zip"
}

resource "aws_lambda_function" "coingecko_proxy" {
  filename         = data.archive_file.lambda_zip.output_path
  function_name    = "coingecko_proxy"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "lambda.handler"
  runtime          = "nodejs20.x"
  source_code_hash = filebase64sha256(data.archive_file.lambda_zip.output_path)
  environment {
    variables = {
      COINGECKO_API_URL = "https://api.coingecko.com/api/v3"
    }
  }
  kms_key_arn = aws_kms_key.lambda_env_var_encryption.arn
  reserved_concurrent_executions = 10
  dead_letter_config {
    target_arn = aws_sqs_queue.dlq.arn
  }
  tracing_config {
    mode = "Active"
  }
  vpc_config {
    subnet_ids         = data.aws_subnets.ct_pub_subnets.ids
    security_group_ids = [lookup(local.security_group_ids, local.env.environment)]
  }
  timeout = 10
}

resource "aws_iam_role" "lambda_exec" {
  name = "LambdaExecutionRole"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_api_gateway_request_validator" "request_validator" {
  rest_api_id = aws_api_gateway_rest_api.currency_cache[0].id
  name        = "request_validator"
  validate_request_body = false
  validate_request_parameters = true
}

resource "aws_api_gateway_method" "coingecko" {
  rest_api_id   = aws_api_gateway_rest_api.currency_cache[0].id
  resource_id   = aws_api_gateway_resource.coingecko.id
  http_method   = "GET"
  authorization = "AWS_IAM"
  request_validator_id = aws_api_gateway_request_validator.request_validator.id
}

resource "aws_api_gateway_integration" "lambda_proxy" {
  rest_api_id             = aws_api_gateway_rest_api.currency_cache[0].id
  resource_id             = aws_api_gateway_resource.coingecko.id
  http_method             = aws_api_gateway_method.coingecko.http_method
  integration_http_method = "GET"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.coingecko_proxy.invoke_arn
}

resource "aws_api_gateway_resource" "coingecko" {
  rest_api_id = aws_api_gateway_rest_api.currency_cache[0].id
  parent_id   = aws_api_gateway_rest_api.currency_cache[0].root_resource_id
  path_part   = "{proxy+}"
}

resource "aws_lambda_permission" "apigw" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.coingecko_proxy.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.currency_cache[0].execution_arn}/*/*"
}