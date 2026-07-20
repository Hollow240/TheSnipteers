# deploy.ps1
# Automates the deployment/update of the ECR image to Amazon ECS (Fargate) in ap-south-1.

$ApiKey = ""
if (-not [string]::IsNullOrEmpty($env:GOOGLE_API_KEY)) {
    $ApiKey = $env:GOOGLE_API_KEY
} else {
    Write-Host "GOOGLE_API_KEY environment variable is not set." -ForegroundColor Yellow
    $SecureKey = Read-Host -Prompt "Please enter your GOOGLE_API_KEY securely" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureKey)
    $ApiKey = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($BSTR)
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($BSTR)
}

if ([string]::IsNullOrEmpty($ApiKey)) {
    Write-Error "GOOGLE_API_KEY cannot be empty. Deployment aborted."
    exit 1
}

$RoleArn = ""
if (-not [string]::IsNullOrEmpty($env:ECS_ROLE_ARN)) {
    $RoleArn = $env:ECS_ROLE_ARN
} else {
    $DefaultRole = "arn:aws:iam::816940507016:role/ecsTaskExecutionRole"
    $RoleArn = Read-Host -Prompt "Enter ECS Task Execution Role ARN [default: $DefaultRole]"
    if ([string]::IsNullOrEmpty($RoleArn)) {
        $RoleArn = $DefaultRole
    }
}

$Subnets = ""
if (-not [string]::IsNullOrEmpty($env:SUBNETS)) {
    $Subnets = $env:SUBNETS
} else {
    $Subnets = Read-Host -Prompt "Enter Subnet IDs (comma-separated, e.g., subnet-123456,subnet-789abc)"
}
if ([string]::IsNullOrEmpty($Subnets)) {
    Write-Error "Subnet IDs are required for Fargate VPC network configuration. Deployment aborted."
    exit 1
}

$SecurityGroup = ""
if (-not [string]::IsNullOrEmpty($env:SECURITY_GROUP)) {
    $SecurityGroup = $env:SECURITY_GROUP
} else {
    $SecurityGroup = Read-Host -Prompt "Enter Security Group ID (e.g., sg-123456)"
}
if ([string]::IsNullOrEmpty($SecurityGroup)) {
    Write-Error "Security Group ID is required. Deployment aborted."
    exit 1
}

# Format subnets into JSON format: e.g. "subnet-123","subnet-456"
$SubnetList = $Subnets.Split(",") | ForEach-Object { "$($_.Trim())" }
$SubnetsFormatted = ($SubnetList | ForEach-Object { "`"$_`"" }) -join ","

$TemplatePath = Join-Path $PSScriptRoot "task-definition.json.template"
$TempPath = Join-Path $PSScriptRoot "task-definition-temp.json"

if (-not (Test-Path $TemplatePath)) {
    Write-Error "Could not find task-definition.json.template at $TemplatePath"
    exit 1
}

# Read template and substitute placeholders
$Config = Get-Content $TemplatePath -Raw
$Config = $Config.Replace("GOOGLE_API_KEY_PLACEHOLDER", $ApiKey)
$Config = $Config.Replace("EXECUTION_ROLE_ARN_PLACEHOLDER", $RoleArn)

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($TempPath, $Config, $Utf8NoBom)

try {
    # 1. Create ECS Cluster
    Write-Host "Ensuring ECS Cluster 'learnanything-cluster' exists..." -ForegroundColor Cyan
    aws ecs create-cluster --cluster-name learnanything-cluster --region ap-south-1 | Out-Null

    # 2. Register Task Definition
    Write-Host "Registering ECS Task Definition 'learnanything-task'..." -ForegroundColor Cyan
    $RegisterOutput = aws ecs register-task-definition --region ap-south-1 --cli-input-json file://task-definition-temp.json | ConvertFrom-Json
    $TaskDefArn = $RegisterOutput.taskDefinition.taskDefinitionArn
    Write-Host "Registered task definition: $TaskDefArn" -ForegroundColor Green

    # 3. Check if service already exists
    $ServiceExists = $false
    $ServiceStatus = aws ecs describe-services --cluster learnanything-cluster --services learnanything-service --region ap-south-1 --query "services[0].status" --output text
    if ($ServiceStatus -eq "ACTIVE") {
        $ServiceExists = $true
    }

    if ($ServiceExists) {
        # Update existing service
        Write-Host "Service already exists. Updating ECS Service 'learnanything-service' with the new task definition..." -ForegroundColor Cyan
        aws ecs update-service --cluster learnanything-cluster --service learnanything-service --task-definition $TaskDefArn --force-new-deployment --region ap-south-1 | Out-Null
    } else {
        # Create new service
        Write-Host "Creating ECS Service 'learnanything-service' on Fargate..." -ForegroundColor Cyan
        aws ecs create-service --cluster learnanything-cluster --service-name learnanything-service --task-definition $TaskDefArn --desired-count 1 --launch-type FARGATE --network-configuration "awsvpcConfiguration={subnets=[$SubnetsFormatted],securityGroups=[$SecurityGroup],assignPublicIp=ENABLED}" --region ap-south-1 | Out-Null
    }

    # 4. Wait for running task and retrieve Public IP
    Write-Host "Waiting for service task to initialize and start..." -ForegroundColor Cyan
    $PublicIp = ""
    for ($i = 0; $i -lt 12; $i++) {
        Start-Sleep -Seconds 10
        Write-Host "Checking task status..."
        $TaskArn = aws ecs list-tasks --cluster learnanything-cluster --service-name learnanything-service --region ap-south-1 --query "taskArns[0]" --output text
        if ($TaskArn -ne "None" -and -not [string]::IsNullOrEmpty($TaskArn)) {
            $TaskStatus = aws ecs describe-tasks --cluster learnanything-cluster --tasks $TaskArn --region ap-south-1 --query "tasks[0].lastStatus" --output text
            Write-Host "Current Task Status: $TaskStatus"
            if ($TaskStatus -eq "RUNNING") {
                # Get the ENI from the task attachments
                $EniId = aws ecs describe-tasks --cluster learnanything-cluster --tasks $TaskArn --region ap-south-1 --query "tasks[0].attachments[0].details[?name=='networkInterfaceId'].value" --output text
                if (-not [string]::IsNullOrEmpty($EniId) -and $EniId -ne "None") {
                    $PublicIp = aws ec2 describe-network-interfaces --network-interface-ids $EniId --region ap-south-1 --query "NetworkInterfaces[0].Association.PublicIp" --output text
                    break
                }
            }
        }
    }

    if (-not [string]::IsNullOrEmpty($PublicIp) -and $PublicIp -ne "None") {
        Write-Host "`n========================================================" -ForegroundColor Green
        Write-Host "Deployment/Update Successful!" -ForegroundColor Green
        Write-Host "Application live URL: http://$PublicIp:8080" -ForegroundColor Green
        Write-Host "========================================================`n" -ForegroundColor Green
    } else {
        Write-Host "Task is running, but failed to retrieve public IP automatically." -ForegroundColor Yellow
        Write-Host "Check the ECS console to retrieve the Public IP manually." -ForegroundColor Yellow
    }
}
finally {
    if (Test-Path $TempPath) {
        Remove-Item $TempPath -Force
        Write-Host "Cleaned up temporary configuration file." -ForegroundColor Green
    }
}
