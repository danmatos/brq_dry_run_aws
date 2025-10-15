variable "project_name" {
  type        = string
  description = "Prefixo para nomear recursos"
  default     = "dry-run-brq"
}

variable "aws_region" {
  type        = string
  description = "Região AWS"
  default     = "sa-east-1"
}

variable "eks_version" {
  type        = string
  default     = "1.29"
}

variable "az_count" {
  type        = number
  default     = 2
}

variable "create_ecr" {
  type        = bool
  default     = true
}

variable "msk_enable" {
  type        = bool
  default     = false
}

variable "alert_email" {
  type        = string
  description = "Email para receber alertas do CloudWatch"
  default     = ""
}
