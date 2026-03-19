terraform {
  required_version = ">= 1.5.0"

  backend "s3" {
    bucket = "auto-repair-shop-51e91ca1-9a1e-4494-b657-6d868a9118de-tfstate"
    key    = "terraform.tfstate"
    region = "us-east-1"
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}
