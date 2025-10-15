#!/bin/bash

# Build and Deploy ETL Applications to EKS
# This script builds the applications, pushes to ECR, and deploys to EKS

set -e

echo "===== ETL Applications Build and Deploy Script ====="

# Configuration
AWS_REGION=${AWS_REGION:-us-east-1}
PROJECT_NAME="aws-eks-msk-starter"

# Get Terraform outputs
echo "Getting Terraform outputs..."
cd terraform
EKS_CLUSTER_NAME=$(terraform output -raw eks_cluster_name)
KAFKA_BOOTSTRAP_SERVERS=$(terraform output -raw msk_bootstrap_brokers_sasl_iam)
S3_INPUT_BUCKET=$(terraform output -raw s3_input_bucket)
S3_REJECTED_BUCKET=$(terraform output -raw s3_rejected_bucket)
S3_REPORTS_BUCKET=$(terraform output -raw s3_reports_bucket)
PRODUCER_ECR_REPO=$(terraform output -raw producer_ecr_repository_url 2>/dev/null || echo "")
CONSUMER_ECR_REPO=$(terraform output -raw consumer_ecr_repository_url 2>/dev/null || echo "")
AGGREGATOR_ECR_REPO=$(terraform output -raw aggregator_ecr_repository_url 2>/dev/null || echo "")

# DynamoDB table names
PROCESSED_FILES_TABLE="${PROJECT_NAME}-processed-files"
TRANSACTIONS_TABLE="${PROJECT_NAME}-transactions"

cd ..

echo "Cluster: $EKS_CLUSTER_NAME"
echo "Kafka Brokers: $KAFKA_BOOTSTRAP_SERVERS"

# Update kubeconfig
echo "Updating kubeconfig..."
aws eks update-kubeconfig --region $AWS_REGION --name $EKS_CLUSTER_NAME

# Build applications
echo "Building applications..."
cd apps
./gradlew clean build

# Build and push Docker images if ECR repositories exist
if [ ! -z "$PRODUCER_ECR_REPO" ]; then
    echo "Building and pushing Producer image..."
    cd producer
    docker build -t $PRODUCER_ECR_REPO:latest .
    aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $PRODUCER_ECR_REPO
    docker push $PRODUCER_ECR_REPO:latest
    cd ..
    
    echo "Building and pushing Consumer image..."
    cd consumer
    docker build -t $CONSUMER_ECR_REPO:latest .
    aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $CONSUMER_ECR_REPO
    docker push $CONSUMER_ECR_REPO:latest
    cd ..
    
    echo "Building and pushing Aggregator image..."
    cd aggregator
    docker build -t $AGGREGATOR_ECR_REPO:latest .
    aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AGGREGATOR_ECR_REPO
    docker push $AGGREGATOR_ECR_REPO:latest
    cd ..
else
    echo "ECR repositories not found. Skipping Docker build and push."
    echo "Make sure to set create_ecr = true in terraform variables and run terraform apply"
fi

cd ..

# Deploy to Kubernetes
echo "Deploying to Kubernetes..."

# Apply namespace and service accounts
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/sa-producer.yaml
kubectl apply -f k8s/sa-consumer.yaml
kubectl apply -f k8s/sa-aggregator.yaml

if [ ! -z "$PRODUCER_ECR_REPO" ]; then
    # Update deployment files with actual values
    echo "Updating deployment configurations..."
    
    # Producer
    sed -e "s|PRODUCER_ECR_IMAGE|$PRODUCER_ECR_REPO:latest|g" \
        -e "s|KAFKA_BOOTSTRAP_SERVERS_PLACEHOLDER|$KAFKA_BOOTSTRAP_SERVERS|g" \
        -e "s|S3_INPUT_BUCKET_PLACEHOLDER|$S3_INPUT_BUCKET|g" \
        -e "s|S3_REJECTED_BUCKET_PLACEHOLDER|$S3_REJECTED_BUCKET|g" \
        -e "s|DYNAMODB_PROCESSED_FILES_TABLE_PLACEHOLDER|$PROCESSED_FILES_TABLE|g" \
        k8s/producer-deployment.yaml | kubectl apply -f -
    
    # Consumer
    sed -e "s|CONSUMER_ECR_IMAGE|$CONSUMER_ECR_REPO:latest|g" \
        -e "s|KAFKA_BOOTSTRAP_SERVERS_PLACEHOLDER|$KAFKA_BOOTSTRAP_SERVERS|g" \
        -e "s|DYNAMODB_TRANSACTIONS_TABLE_PLACEHOLDER|$TRANSACTIONS_TABLE|g" \
        k8s/consumer-deployment.yaml | kubectl apply -f -
    
    # Aggregator
    sed -e "s|AGGREGATOR_ECR_IMAGE|$AGGREGATOR_ECR_REPO:latest|g" \
        -e "s|KAFKA_BOOTSTRAP_SERVERS_PLACEHOLDER|$KAFKA_BOOTSTRAP_SERVERS|g" \
        -e "s|S3_REPORTS_BUCKET_PLACEHOLDER|$S3_REPORTS_BUCKET|g" \
        k8s/aggregator-deployment.yaml | kubectl apply -f -
    
    echo "Deployment complete!"
    echo ""
    echo "Check deployment status:"
    echo "kubectl get pods -n etl"
    echo ""
    echo "View logs:"
    echo "kubectl logs -f deployment/etl-producer -n etl"
    echo "kubectl logs -f deployment/etl-consumer -n etl"
    echo "kubectl logs -f deployment/etl-aggregator -n etl"
else
    echo "Skipping Kubernetes deployment - ECR images not available"
fi
