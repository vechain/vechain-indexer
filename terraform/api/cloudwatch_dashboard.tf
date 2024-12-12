locals {
  dashboard_body = {
    "widgets" : [
      for k, v in merge({for k, v in module.ecs-backend-service : v.service_name => v.cluster_name}, {
        for k, v in module.ecs-lb-service-api : v.service_name => v.cluster_name
      }) : {
        "type" : "metric",
        "x" : 0,
        "y" : 0,
        "width" : 12,
        "height" : 6,
        "properties" : {
          "metrics" : [
            ["AWS/ECS", "CPUUtilization", "ServiceName", k, "ClusterName", v],
            [".", "MemoryUtilization", ".", ".", ".", "."]
          ],
          "view" : "timeSeries",
          "stacked" : false,
          region : "${local.env.region}",
          "stat" : "Maximum",
          "period" : 300,
          "title" : "${k} CPU and Memory",
          "legend" : {
            "position" : "bottom"
          },
          "yAxis" : {
            "left" : {
              "min" : 0,
              "max" : 100
            },
            "right" : {
              "min" : 0,
              "max" : 100
            }
          }
        }
      }
    ],
    "start" : "-PT3H",
    "end" : "P0D"
  }

  api_id = try(data.terraform_remote_state.vpc.outputs.currency_cache_name, "")

  count = [
    "AWS/ApiGateway", "Count", "ApiName", local.api_id, { id : "m1", region : local.env.region, "visible" : false }
  ]
  too_xx_results = [{ expression : "m1-m2-m3", label : "2XX", id : "e1", color : "#2ca02c", region : local.env.region }]
  fife_xx_errors = [
    ".", "5XXError", ".", ".", { region : local.env.region, id : "m2", color : "#d62728" }
  ]
  fower_xx_errors = [
    ".", "4XXError", ".", ".", { region : local.env.region, id : "m3", color : "#9467bd" }
  ]

  cache_hit_count = [
    ".", "CacheHitCount", ".", ".",
    { region : local.env.region, id : "m4", color : "#17becf", "label" : "CacheHitCount", "yAxis" : "right" }
  ]
  cache_miss_count = [
    ".", "CacheMissCount", ".", ".",
    { region : local.env.region, id : "m5", color : "#dbdb8d", "label" : "CacheMissCount", "yAxis" : "right" }
  ]
  api_gateway_dash_code = {
    "widgets" : [
      {
        "type" : "metric",
        "x" : 0,
        "y" : 0,
        "width" : 12,
        "height" : 6,
        "properties" : {
          "metrics" : [
            local.too_xx_results,
            local.count,
            local.fower_xx_errors,
            local.fife_xx_errors,
            local.cache_hit_count,
            local.cache_miss_count

          ],
          "view" : "timeSeries",
          "stacked" : false,
          region : local.env.region,
          "stat" : "Sum",
          "title" : "Coin Api Gw Stats",

        }
      },
      {
        height: 6,
        width: 12,
        y: 0,
        x: 12,
        type: "metric",
        properties: {
          metrics: [
            [ "AWS/ApiGateway", "5XXError", "ApiName", local.api_id, "Resource", "/coins/{coin_id}", "Stage", "default", "Method", "GET", { id: "m12", region: local.env.region, color: "#d62728" } ],
            [ ".", "Count", ".", ".", ".", ".", ".", ".", ".", ".", { id: "m13", region: local.env.region, yAxis: "right", color: "#2ca02c" } ],
            [ ".", "4XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id: "m14", region: local.env.region, color: "#ff7f0e" } ],
            [ ".", "CacheHitCount", ".", ".", ".", ".", ".", ".", ".", ".", { id: "m15", region: local.env.region, yAxis: "right", color: "#9edae5" } ]
          ],
          region: local.env.region,
          stacked: true,
          stat: "Sum",
          title: "Coin Api <coin_id> GET",
          view: "timeSeries"
        }
      },
      {
        height: 6,
        width: 12,
        y: 6,
        x: 0,
        type: "metric",
        properties: {
          metrics: [
            [ "AWS/ApiGateway", "5XXError", "ApiName", local.api_id, "Resource", "/coins/{coin_id}/market_chart", "Stage", "default", "Method", "GET", { id: "m16", region: local.env.region, color: "#d62728" } ],
            [ ".", "Count", ".", ".", ".", ".", ".", ".", ".", ".", { id: "m17", region: local.env.region, color: "#2ca02c", yAxis: "right" } ],
            [ ".", "4XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id: "m18", region: local.env.region, color: "#ff7f0e" } ],
            [ ".", "CacheHitCount", ".", ".", ".", ".", ".", ".", ".", ".", { id: "m20", region: local.env.region, color: "#9edae5", yAxis: "right" } ]
          ],
          region: local.env.region,
          stacked: true,
          stat: "Sum",
          title: "Coin Api <coin_id>/market_chart GET",
          view: "timeSeries"
        }
      }
    ]
  }
}

# module "aws_cloudwatch_dashboard_gw" {
#   source                      = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudwatchdashboard?ref=v.1.0.2"
#   create_cloudwatch_dashboard = true
#   dashboard_name              = "coin-currency-cache-${local.env.environment}-dashboard"
#   dashboard_body = jsonencode(local.api_gateway_dash_code)
# }

module "aws_cloudwatch_dashboard" {
  source                      = "git::git@github.com:/vechainfoundation/terraform_infrastructure_modules.git//cloudwatchdashboard?ref=v.1.0.2"
  create_cloudwatch_dashboard = true
  dashboard_name              = "veworld-indexer-${local.env.environment}-dashboard"
  dashboard_body              = jsonencode(local.dashboard_body)
}
