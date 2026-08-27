#!/bin/bash
# Terraform Workspace Management Script for CloudFront Infrastructure

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 <command> [workspace] [additional_args]"
    echo ""
    echo "Commands:"
    echo "  init                     - Initialize Terraform and create workspaces"
    echo "  plan <workspace>         - Run terraform plan for specified workspace"
    echo "  apply <workspace>        - Run terraform apply for specified workspace"
    echo "  destroy <workspace>      - Run terraform destroy for specified workspace"
    echo "  list                     - List all available workspaces"
    echo "  show <workspace>         - Show current workspace state"
    echo "  select <workspace>       - Select a workspace"
    echo "  validate                 - Validate configuration in current workspace"
    echo "  deploy-all               - Deploy all environments in order (shared -> staging -> prod)"
    echo ""
    echo "Workspaces:"
    echo "  shared                   - Shared resources (cache policies, WAF)"
    echo "  staging                  - Staging environment"
    echo "  prod                     - Production environment"
    echo ""
    echo "Examples:"
    echo "  $0 init"
    echo "  $0 plan shared"
    echo "  $0 apply staging"
    echo "  $0 deploy-all"
}

# Function to initialize Terraform and create workspaces
init_terraform() {
    print_status "Initializing Terraform..."
    terraform init

    print_status "Creating workspaces..."
    
    # Create workspaces if they don't exist
    for workspace in shared staging prod; do
        if ! terraform workspace list | grep -q "$workspace"; then
            print_status "Creating workspace: $workspace"
            terraform workspace new "$workspace"
        else
            print_success "Workspace $workspace already exists"
        fi
    done
    
    # Switch back to default workspace
    terraform workspace select default
    print_success "Terraform initialization complete!"
}

# Function to validate workspace name
validate_workspace() {
    local workspace=$1
    if [[ ! "$workspace" =~ ^(shared|staging|prod)$ ]]; then
        print_error "Invalid workspace: $workspace"
        print_error "Valid workspaces: shared, staging, prod"
        exit 1
    fi
}

# Function to plan deployment
plan_deployment() {
    local workspace=$1
    validate_workspace "$workspace"
    
    print_status "Planning deployment for workspace: $workspace"
    terraform workspace select "$workspace"
    terraform plan -out="$workspace.tfplan"
    print_success "Plan completed for $workspace"
}

# Function to apply deployment
apply_deployment() {
    local workspace=$1
    validate_workspace "$workspace"
    
    print_status "Applying deployment for workspace: $workspace"
    terraform workspace select "$workspace"
    
    if [ -f "$workspace.tfplan" ]; then
        terraform apply "$workspace.tfplan"
        rm "$workspace.tfplan"
    else
        print_warning "No plan file found, running apply without plan..."
        terraform apply -auto-approve
    fi
    
    print_success "Deployment completed for $workspace"
}

# Function to destroy resources
destroy_deployment() {
    local workspace=$1
    validate_workspace "$workspace"
    
    print_warning "This will DESTROY all resources in workspace: $workspace"
    read -p "Are you sure? (type 'yes' to confirm): " confirm
    
    if [ "$confirm" = "yes" ]; then
        print_status "Destroying resources in workspace: $workspace"
        terraform workspace select "$workspace"
        terraform destroy -auto-approve
        print_success "Resources destroyed in $workspace"
    else
        print_status "Destruction cancelled"
    fi
}

# Function to deploy all environments in order
deploy_all() {
    print_status "Starting full deployment process..."
    
    # Deploy shared resources first
    print_status "Step 1/3: Deploying shared resources..."
    plan_deployment "shared"
    apply_deployment "shared"
    
    # Deploy staging environment
    print_status "Step 2/3: Deploying staging environment..."
    plan_deployment "staging"
    apply_deployment "staging"
    
    # Deploy production environment
    print_status "Step 3/3: Deploying production environment..."
    plan_deployment "prod"
    print_warning "Production deployment requires manual approval"
    read -p "Deploy to production? (type 'yes' to confirm): " confirm
    
    if [ "$confirm" = "yes" ]; then
        apply_deployment "prod"
        print_success "Full deployment completed successfully!"
    else
        print_status "Production deployment skipped"
        print_status "Run '$0 apply prod' when ready to deploy to production"
    fi
}

# Function to list workspaces
list_workspaces() {
    print_status "Available workspaces:"
    terraform workspace list
}

# Function to show workspace state
show_workspace() {
    local workspace=$1
    validate_workspace "$workspace"
    
    terraform workspace select "$workspace"
    print_status "Current workspace: $(terraform workspace show)"
    terraform show
}

# Function to select workspace
select_workspace() {
    local workspace=$1
    validate_workspace "$workspace"
    
    terraform workspace select "$workspace"
    print_success "Selected workspace: $workspace"
}

# Function to validate configuration
validate_config() {
    print_status "Validating Terraform configuration..."
    terraform validate
    print_success "Configuration is valid!"
}

# Main script logic
case "${1:-}" in
    init)
        init_terraform
        ;;
    plan)
        if [ -z "${2:-}" ]; then
            print_error "Workspace name required for plan command"
            show_usage
            exit 1
        fi
        plan_deployment "$2"
        ;;
    apply)
        if [ -z "${2:-}" ]; then
            print_error "Workspace name required for apply command"
            show_usage
            exit 1
        fi
        apply_deployment "$2"
        ;;
    destroy)
        if [ -z "${2:-}" ]; then
            print_error "Workspace name required for destroy command"
            show_usage
            exit 1
        fi
        destroy_deployment "$2"
        ;;
    deploy-all)
        deploy_all
        ;;
    list)
        list_workspaces
        ;;
    show)
        if [ -z "${2:-}" ]; then
            print_error "Workspace name required for show command"
            show_usage
            exit 1
        fi
        show_workspace "$2"
        ;;
    select)
        if [ -z "${2:-}" ]; then
            print_error "Workspace name required for select command"
            show_usage
            exit 1
        fi
        select_workspace "$2"
        ;;
    validate)
        validate_config
        ;;
    *)
        print_error "Unknown command: ${1:-}"
        show_usage
        exit 1
        ;;
esac

