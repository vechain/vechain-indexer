# PostgreSQL: one RDS instance per colour per network, home of blocks and transactions

# Set by the restore workflow so a dead colour is rebuilt from the live colour's snapshot;
# absent, the instance is created empty and backfills from Thor.
variable "pg_snapshot_override" {
  description = "Per-net RDS snapshot identifier to create the instance from, e.g. {main = \"rds:prod-blue-main-pg-2026-09-11-07-05\"}"
  type        = map(string)
  default     = {}
}

locals {
  pg_nets    = { for net, cfg in local.env.enabled_nets : net => cfg.postgres if try(cfg.postgres.enabled, false) }
  pg_enabled = length(local.pg_nets) > 0

  # jdbc:postgresql://host:port/vechain per net; empty where Postgres is off, which the apps refuse.
  pg_url = {
    for net in keys(local.env.enabled_nets) :
    net => try("jdbc:postgresql://${aws_db_instance.postgres[net].address}:${aws_db_instance.postgres[net].port}/vechain", "")
  }
}

resource "aws_db_subnet_group" "postgres" {
  count      = local.pg_enabled ? 1 : 0
  name       = "${local.env.environment}-${var.project}-pg"
  subnet_ids = data.terraform_remote_state.vpc.outputs.private_subnets

  tags = {
    Environment = local.env.environment
    Name        = "${local.env.environment}-${var.project}-pg"
  }
}

# The API tasks run in alb-sg (see module ecs-lb-service-api), the indexer tasks in ecs_service_sg.
resource "aws_security_group" "postgres" {
  count       = local.pg_enabled ? 1 : 0
  name        = "${local.env.environment}-${var.project}-sg-postgres"
  description = "PostgreSQL ingress from the indexer and API tasks"
  vpc_id      = data.terraform_remote_state.vpc.outputs.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_service_sg.id, aws_security_group.alb-sg.id]
  }

  tags = {
    Environment = local.env.environment
    Name        = "${local.env.environment}-${var.project}-sg-postgres"
  }
}

resource "aws_db_parameter_group" "postgres" {
  count  = local.pg_enabled ? 1 : 0
  name   = "${local.env.environment}-${var.project}-pg16"
  family = "postgres16"

  parameter {
    name         = "shared_preload_libraries"
    value        = "pg_stat_statements"
    apply_method = "pending-reboot"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  # gp3 is SSD: random reads cost about the same as sequential ones.
  parameter {
    name  = "random_page_cost"
    value = "1.1"
  }

  # Append-only tables: vacuum (and so freeze) every 2% of inserts rather than the default 20%.
  parameter {
    name  = "autovacuum_vacuum_insert_scale_factor"
    value = "0.02"
  }

  parameter {
    name  = "autovacuum_vacuum_insert_threshold"
    value = "1000000"
  }

  tags = {
    Environment = local.env.environment
  }
}

# Credentials: one password per role per colour, in Secrets Manager like the Mongo ones

resource "random_password" "pg_indexer_password" {
  length           = 24
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>?"
}

resource "random_password" "pg_api_password" {
  length           = 24
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>?"
}

resource "aws_secretsmanager_secret" "pg_indexer_password" {
  name = "/${local.env.environment}/${local.env.project}/pg_indexer_password"
}

resource "aws_secretsmanager_secret" "pg_api_password" {
  name = "/${local.env.environment}/${local.env.project}/pg_api_password"
}

resource "aws_secretsmanager_secret_version" "pg_indexer_password" {
  secret_id     = aws_secretsmanager_secret.pg_indexer_password.id
  secret_string = random_password.pg_indexer_password.result
}

resource "aws_secretsmanager_secret_version" "pg_api_password" {
  secret_id     = aws_secretsmanager_secret.pg_api_password.id
  secret_string = random_password.pg_api_password.result
}

# Instances

# The identifier fixes the hostname, so a replace-from-snapshot keeps the endpoint. A colour is
# disposable: durable copies are the live colour's automated backups, hence no final snapshot.
resource "aws_db_instance" "postgres" {
  for_each = local.pg_nets

  identifier     = "${local.env.environment}-${each.key}-pg"
  engine         = "postgres"
  engine_version = each.value.engine_version
  instance_class = each.value.instance_class

  allocated_storage     = each.value.allocated_storage_gb
  max_allocated_storage = each.value.max_allocated_storage_gb
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = "vechain"
  username = "indexer"
  password = random_password.pg_indexer_password.result

  db_subnet_group_name   = aws_db_subnet_group.postgres[0].name
  vpc_security_group_ids = [aws_security_group.postgres[0].id]
  parameter_group_name   = aws_db_parameter_group.postgres[0].name
  publicly_accessible    = false
  multi_az               = each.value.multi_az

  # Daily snapshot lands beside the Atlas one (reference_hour_of_day = 7), so both restores of a
  # dead colour start from roughly the same chain head.
  backup_retention_period = 7
  backup_window           = "06:00-07:00"
  maintenance_window      = "sun:03:00-sun:04:00"
  copy_tags_to_snapshot   = true

  performance_insights_enabled          = true
  performance_insights_retention_period = 7
  enabled_cloudwatch_logs_exports       = ["postgresql"]
  auto_minor_version_upgrade            = true
  apply_immediately                     = true

  deletion_protection = false
  skip_final_snapshot = true
  snapshot_identifier = lookup(var.pg_snapshot_override, each.key, null)

  tags = {
    Environment = local.env.environment
    Network     = each.key
    Backup      = "${var.project}-pg"
  }

  # Storage autoscaling grows it out of band, as the Atlas disk does.
  # snapshot_identifier is ForceNew: a later empty override must not replace the restored instance.
  lifecycle {
    ignore_changes = [allocated_storage, snapshot_identifier]
  }
}

# Prewarm task: a restored instance lazy-loads its blocks from S3, so pull them before traffic

resource "aws_cloudwatch_log_group" "pg_prewarm" {
  count             = local.pg_enabled ? 1 : 0
  name              = "/ecs/${local.env.environment}-pg-prewarm"
  retention_in_days = 14
}

resource "aws_iam_role" "pg_prewarm_execution" {
  count = local.pg_enabled ? 1 : 0
  name  = "${local.env.environment}-pg-prewarm-execution"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "pg_prewarm_execution" {
  count      = local.pg_enabled ? 1 : 0
  role       = aws_iam_role.pg_prewarm_execution[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Run with `aws ecs run-task` inside the service security group; the task exits when every table
# and index in Postgres has been read once.
resource "aws_ecs_task_definition" "pg_prewarm" {
  for_each = local.pg_nets

  family                   = "${local.env.environment}-${each.key}-pg-prewarm"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.pg_prewarm_execution[0].arn

  container_definitions = jsonencode([{
    name      = "psql"
    image     = "public.ecr.aws/docker/library/postgres:16"
    essential = true
    command = [
      "psql", "-v", "ON_ERROR_STOP=1",
      "-c", "CREATE EXTENSION IF NOT EXISTS pg_prewarm",
      "-c", "CREATE EXTENSION IF NOT EXISTS pg_stat_statements",
      "-c", "SELECT c.oid::regclass AS relation, pg_prewarm(c.oid) AS pages FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public' AND c.relkind IN ('r', 'i') ORDER BY 1",
    ]
    environment = [
      { name = "PGHOST", value = aws_db_instance.postgres[each.key].address },
      { name = "PGPORT", value = tostring(aws_db_instance.postgres[each.key].port) },
      { name = "PGDATABASE", value = "vechain" },
      { name = "PGUSER", value = "indexer" },
      { name = "PGPASSWORD", value = random_password.pg_indexer_password.result },
      { name = "PGSSLMODE", value = "require" },
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.pg_prewarm[0].name
        awslogs-region        = local.env.region
        awslogs-stream-prefix = each.key
      }
    }
  }])

  tags = {
    Environment = local.env.environment
    Network     = each.key
  }
}
