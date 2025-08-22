# Connect to Stripe Application

Spring Boot application that integrates Stripe payments with Mailchimp audience management.

## Prerequisites

- Java 17
- Maven
- Docker
- AWS CLI configured with ECR permissions

## Local Development

### Build and Run Locally

```bash
# Build the application
./mvnw clean package -DskipTests

# Run locally
java -jar target/connecttostripe-1.0.0.jar
```

### Run with Docker

```bash
# Build Docker image (for local development)
docker build -t connecttostripe .

# Build for AWS deployment (x86_64/AMD64 platform)
docker build --platform linux/amd64 -t connecttostripe .

# Run container (you must provide database connection details)
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/connecttostripe \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=password \
  -e STRIPE_SIGNING_SECRET=your_stripe_secret \
  -e STRIPE_ENDPOINT_SECRET=your_endpoint_secret \
  -e MAILCHIMP_CLIENT_ID=your_mailchimp_id \
  -e MAILCHIMP_CLIENT_SECRET=your_mailchimp_secret \
  -e MAILCHIMP_REDIRECT_URI=http://localhost:8080/api/oauth/mailchimp/callback \
  -e MAILCHIMP_STRIPE_REDIRECT_URI=your_stripe_redirect \
  connecttostripe

# Access application at http://localhost:8080
```

### Run with Docker Compose

Create `.env` file with your database and API credentials, then:
```bash
docker-compose up --build
```

## Docker Build and Push to AWS ECR

### 1. Build JAR file locally
```bash
./mvnw clean package -DskipTests
```

### 2. Login to AWS ECR
```bash
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 445965045266.dkr.ecr.us-east-1.amazonaws.com
```

### 3. Build Docker image for AWS (x86_64/AMD64 platform)
```bash
docker build --platform linux/amd64 -t connecttostripe .
```

### 4. Tag for ECR
```bash
docker tag connecttostripe:latest 445965045266.dkr.ecr.us-east-1.amazonaws.com/connecttostripe:latest
```

### 5. Push to ECR
```bash
docker push 445965045266.dkr.ecr.us-east-1.amazonaws.com/connecttostripe:latest
```

## Environment Variables

Set these environment variables for production deployment:

- `SPRING_DATASOURCE_URL` - PostgreSQL connection URL
- `SPRING_DATASOURCE_USERNAME` - Database username
- `SPRING_DATASOURCE_PASSWORD` - Database password
- `STRIPE_SIGNING_SECRET` - Stripe webhook signing secret
- `STRIPE_ENDPOINT_SECRET` - Stripe endpoint secret
- `MAILCHIMP_CLIENT_ID` - Mailchimp OAuth client ID
- `MAILCHIMP_CLIENT_SECRET` - Mailchimp OAuth client secret
- `MAILCHIMP_REDIRECT_URI` - Mailchimp OAuth callback URL
- `MAILCHIMP_STRIPE_REDIRECT_URI` - Stripe redirect URL

## AWS Elastic Beanstalk Deployment

### Using ECR Image with RDS Database

Create `Dockerrun.aws.json`:
```json
{
  "AWSEBDockerrunVersion": "1",
  "Image": {
    "Name": "445965045266.dkr.ecr.us-east-1.amazonaws.com/connecttostripe:latest",
    "Update": "true"
  },
  "Ports": [
    {
      "ContainerPort": "8080"
    }
  ]
}
```

Deploy this file to Elastic Beanstalk and configure environment variables in the EB console:

**Required Environment Variables:**
- `SPRING_DATASOURCE_URL` - Your RDS PostgreSQL endpoint: `jdbc:postgresql://your-rds-endpoint.amazonaws.com:5432/your-database`
- `SPRING_DATASOURCE_USERNAME` - Your RDS username
- `SPRING_DATASOURCE_PASSWORD` - Your RDS password
- (Plus all other environment variables listed below)

**Security Group Configuration:**
- Ensure Elastic Beanstalk security group can connect to RDS on port 5432
- RDS security group should allow inbound PostgreSQL (5432) from EB security group

## Database

Uses PostgreSQL with Flyway migrations. Database schema is automatically created on startup.

**Database Connection Required:**
- Application requires PostgreSQL database connection to start
- Local: Connect to your existing PostgreSQL instance
- AWS: Connect to RDS PostgreSQL instance
- Configure via environment variables (app will not start without valid database connection)

Migration files are located in `src/main/resources/db/migration/`.

## API Endpoints

- Health check: `/actuator/health` (add spring-boot-actuator dependency if needed)
- Stripe webhooks: `/stripe/webhook`
- Mailchimp OAuth: `/api/oauth/mailchimp/callback`