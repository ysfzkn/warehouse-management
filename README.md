<div align="center">

# 📦 Warehouse Management System

**A modern, enterprise-grade warehouse management solution for inventory tracking and control**

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1.5-6DB33F?style=for-the-badge&logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.2.0-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://reactjs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-336791?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

[Features](#-features) • [Quick Start](#-quick-start) • [Tech Stack](#-tech-stack) • [API Docs](#-api-documentation) • [Deployment](#-deployment) • [Contributing](#-contributing)

</div>

---

## 🎯 Overview

A comprehensive warehouse management system designed for businesses that need efficient inventory tracking, multi-warehouse support, and real-time stock management. Built with modern technologies and best practices, this system provides a scalable solution for warehouse operations.

### Key Highlights

- 🏭 **Multi-Warehouse Support** - Manage unlimited warehouses with independent inventory tracking
- 📊 **Real-Time Dashboard** - Live statistics, low stock alerts, and comprehensive analytics
- 🔄 **Stock Transfers** - Seamless product transfers between warehouses with status tracking
- 📱 **Mobile Responsive** - Full functionality on any device with responsive design
- 🔒 **Enterprise Ready** - Clean architecture, exception handling, and production-ready code
- 🚀 **Easy Deployment** - Docker support and cloud-ready configuration

---

## ✨ Features

### Core Functionality

| Feature | Description |
|---------|-------------|
| **Product Management** | Complete CRUD operations with categorization, SKU tracking, and product attributes |
| **Inventory Control** | Real-time stock levels, minimum stock alerts, reserved quantities, and consignment tracking |
| **Warehouse Operations** | Multi-warehouse management with activation/deactivation and capacity tracking |
| **Stock Transfers** | Inter-warehouse transfers with PENDING → IN_TRANSIT → COMPLETED workflow |
| **Category System** | Flexible product categorization with hierarchical support |
| **Brand & Color Management** | Organize products by brands and color variants |
| **Low Stock Alerts** | Automatic notifications when stock falls below minimum levels |
| **Advanced Filtering** | Filter products by category, brand, color, and warehouse |

### Technical Features

- ✅ **RESTful API** - Clean, documented endpoints following REST principles
- ✅ **Exception Handling** - Centralized error management with custom error codes
- ✅ **Data Validation** - Input validation with meaningful error messages
- ✅ **Transaction Management** - Database transactions for data integrity
- ✅ **Unit Testing** - Comprehensive test coverage with Mockito
- ✅ **Clean Code** - SOLID principles, DRY, and best practices

---

## 🚀 Quick Start

### Prerequisites

- Docker & Docker Compose (recommended)
- Java 17+ (for local development)
- Node.js 18+ (for frontend development)

### Option 1: Docker (Recommended)

   ```bash
# Clone the repository
git clone https://github.com/yourusername/warehouse-management.git
cd warehouse-management

# Start all services
docker-compose up -d

# Access the application
# Frontend: http://localhost
# Backend API: http://localhost/api
# Database: PostgreSQL on port 5432
```

### Option 2: Manual Setup

**Backend:**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

**Frontend:**
   ```bash
   cd frontend
   npm install
   npm start
   ```

---

## 🧬 Database Migrations (Flyway)

- All schema changes live under `src/main/resources/db/migration` (starting with `V1__init_schema.sql`).
- Apply migrations manually with the bundled Maven plugin:
  ```bash
  mvn -Dflyway.url=jdbc:postgresql://localhost:5432/warehouse_db \
      -Dflyway.user=warehouse_user \
      -Dflyway.password=warehouse_pass \
      flyway:migrate
  ```
- `deploy.sh` now spins up the local PostgreSQL container and runs Flyway before building the stack (set `SKIP_FLYWAY=true ./deploy.sh` to bypass this step).
- Spring Boot also triggers Flyway on startup; set `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` (and optionally `SPRING_FLYWAY_BASELINE_VERSION=1`) **once** if you need to baseline an already populated database before adopting migrations.

---

## 🛠️ Tech Stack

### Backend
- **Framework:** Spring Boot 3.1.5
- **Language:** Java 17
- **ORM:** Spring Data JPA / Hibernate
- **Database:** PostgreSQL 15 (production), H2 (development)
- **Migrations:** Flyway 10.x
- **Build Tool:** Maven
- **Testing:** JUnit 5, Mockito

### Frontend
- **Library:** React 18.2.0
- **UI Framework:** Bootstrap 5
- **HTTP Client:** Axios
- **State Management:** React Hooks

### DevOps
- **Containerization:** Docker & Docker Compose
- **Web Server:** Nginx (production)
- **CI/CD:** GitHub Actions ready

---

## 🏗️ Architecture

```
┌─────────────────────┐
│   React Frontend    │
│  (Port 3000/80)     │
└──────────┬──────────┘
           │ HTTP/REST
           ↓
┌─────────────────────┐
│  Spring Boot API    │
│    (Port 8080)      │
│                     │
│  ┌───────────────┐  │
│  │  Controllers  │  │
│  ├───────────────┤  │
│  │   Services    │  │
│  ├───────────────┤  │
│  │ Repositories  │  │
│  └───────┬───────┘  │
└──────────┼──────────┘
           │ JPA/Hibernate
           ↓
┌─────────────────────┐
│   PostgreSQL DB     │
│    (Port 5432)      │
└─────────────────────┘
```

### Project Structure

```
warehouse-management/
├── src/main/java/com/warehouse/
│   ├── controller/      # REST API endpoints
│   ├── service/         # Business logic
│   ├── repository/      # Data access layer
│   ├── entity/          # JPA entities
│   ├── dto/             # Data transfer objects
│   ├── exception/       # Custom exceptions & error handling
│   ├── util/            # Utility classes
│   └── config/          # Configuration classes
├── frontend/
│   ├── src/
│   │   ├── components/  # React components
│   │   └── pages/       # Page components
│   └── public/
├── docker-compose.yml
└── Dockerfile
```

---

## 📚 API Documentation

### Main Endpoints

| Resource | Methods | Description |
|----------|---------|-------------|
| `/api/products` | GET, POST, PUT, DELETE | Product management |
| `/api/categories` | GET, POST, PUT, DELETE | Category operations |
| `/api/warehouses` | GET, POST, PUT, DELETE | Warehouse management |
| `/api/stocks` | GET, POST, PUT, DELETE | Stock operations |
| `/api/stock-transfers` | GET, POST, PUT, DELETE | Transfer management |
| `/api/brands` | GET, POST, PUT, DELETE | Brand operations |
| `/api/colors` | GET, POST, PUT, DELETE | Color management |

### Example: Create Product

```http
POST /api/products
Content-Type: application/json

{
  "name": "Industrial Refrigerator",
  "sku": "REF-2024-001",
  "price": 15000.00,
  "weight": 120.5,
  "dimensions": "80x80x200 cm",
  "category": { "id": 1 },
  "brand": { "id": 2 },
  "color": { "id": 3 }
}
```

### Error Response Format

```json
{
  "errorCode": "PRODUCT_001",
  "status": 404,
  "error": "Not Found",
  "message": "Product not found - ID: 123",
  "path": "/api/products/123",
  "timestamp": "2024-01-15T10:30:00"
}
```

For complete API documentation, see [API_README.md](API_README.md).

---

## ⚙️ Configuration

### Environment Variables

```properties
# Database Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/warehouse_db
SPRING_DATASOURCE_USERNAME=your_username
SPRING_DATASOURCE_PASSWORD=your_password

# Application Profile
SPRING_PROFILES_ACTIVE=prod

# Flyway (optional overrides)
SPRING_FLYWAY_BASELINE_ON_MIGRATE=false
SPRING_FLYWAY_BASELINE_VERSION=1
SPRING_FLYWAY_BASELINE_DESCRIPTION="Existing schema"

# Server Configuration
SERVER_PORT=8080
```

`deploy.sh` also understands `SKIP_FLYWAY=true` if you need to skip the pre-build migration step locally.

### Application Profiles

- **dev** - H2 in-memory database for development
- **prod** - PostgreSQL for production

---

## 🚀 Deployment

### Docker Deployment

```bash
# Production build
docker-compose up -d

# Check logs
docker-compose logs -f

# Stop services
docker-compose down
```

> Use `./deploy.sh` for an opinionated flow that also runs Flyway migrations before the containers are rebuilt. Set `SKIP_FLYWAY=true` if you only need to rebuild images.

### Cloud Deployment

The application is ready for deployment on:

- **Railway** - Recommended for quick deployment
- **Heroku** - Full support with Procfile
- **AWS/GCP/Azure** - Docker-ready for any cloud platform
- **VPS** - Deploy with Docker Compose

For VPS or bare-metal deployments you can reuse `nginx/prod.conf` as-is; it proxies `/` to the React frontend and `/api/**` (including `/api/stream` and `/api/actuator/health`) to the Spring Boot service, so no Flyway-specific changes are needed.

#### Railway + Flyway pipeline

1. Install the Railway CLI (`npm install -g @railway/cli`) and log in.
2. `railway init` inside this repository, then `railway add postgresql` to provision a managed database.
3. Expose the generated credentials as environment variables (`DATABASE_URL`, `PGUSER`, `PGPASSWORD`, etc.).
4. Run migrations before deploying the service:
   ```bash
   railway run "mvn -DskipTests -Dflyway.url=$DATABASE_URL \
       -Dflyway.user=$PGUSER \
       -Dflyway.password=$PGPASSWORD \
       flyway:migrate"
   ```
5. For existing Railway databases, temporarily set `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` (or pass `-Dflyway.baselineOnMigrate=true`) for a single baseline run, then revert to `false`.
6. Deploy the application with `railway up`; the platform builds from the root `Dockerfile`, and Flyway will validate migrations again during startup.

### Environment Setup

1. Set environment variables
2. Configure PostgreSQL connection
3. Run Flyway migrations (`mvn flyway:migrate`, `deploy.sh`, or during app startup)
4. Deploy with Docker or build from source

---

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=ProductServiceTest

# Run with coverage
mvn test jacoco:report
```

Current test coverage:
- ✅ Service Layer: 30+ unit tests
- ✅ Mapper Layer: 6+ unit tests
- ✅ Exception Handling: Comprehensive coverage

---

## 📊 Database Schema

### Core Entities

- **Products** - Product information with SKU, pricing, and attributes
- **Categories** - Product categorization system
- **Warehouses** - Warehouse locations and details
- **Stocks** - Inventory records linking products to warehouses
- **Stock Transfers** - Transfer records between warehouses
- **Brands** - Product brand management
- **Colors** - Product color variants

All entities include automatic timestamp tracking (`createdAt`, `updatedAt`) and support soft deletion where applicable.

---

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Standards

- Follow SOLID principles
- Write unit tests for new features
- Use meaningful commit messages
- Update documentation as needed

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🆘 Support

- **Documentation:** [API_README.md](API_README.md) | [QUICK_START.md](QUICK_START.md)
- **Issues:** [GitHub Issues](https://github.com/yourusername/warehouse-management/issues)
- **Email:** support@example.com

---

## 🗺️ Roadmap

- [ ] Multi-language support (i18n)
- [ ] Advanced reporting and analytics
- [ ] Barcode/QR code integration
- [ ] Mobile app (React Native)
- [ ] Real-time notifications (WebSocket)
- [ ] Role-based access control (RBAC)
- [ ] API rate limiting
- [ ] Export functionality (PDF, Excel)

---

## 👥 Author

Developed by **Yusuf Ozkan** ([@ysfzkn](https://github.com/ysfzkn))

---

<div align="center">

**[⬆ Back to Top](#-warehouse-management-system)**

Made with ☕ and lots of code

⭐ Star this repository if you find it helpful!

</div>
