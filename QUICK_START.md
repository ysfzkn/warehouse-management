# 🚀 Quick Start Guide

Get the Warehouse Management System up and running in minutes.

---

## Prerequisites

- **Docker & Docker Compose** (recommended)
- **Git**
- **Java 21+** and **Maven 3.9+** (only for manual backend setup)
- **Node.js 18+** (only for manual frontend setup)

---

## Installation

### Step 1: Clone the Repository

```bash
git clone https://github.com/yourusername/warehouse-management.git
cd warehouse-management
```

### Step 2: Start with Docker

```bash
# Start all services
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f
```

### Step 3: Access the Application

| Service | URL | Description |
|---------|-----|-------------|
| **Frontend** | http://localhost | Main application |
| **Backend API** | http://localhost/api | REST API |
| **H2 Console** | http://localhost:8080/h2-console | Database console (dev) |

---

## Alternative: Manual Setup

### Backend

```bash
# Install dependencies
mvn clean install

# Run application
mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm start
```

---

## First Steps

1. **Create a Category**
   - Navigate to Categories
   - Click "New Category"
   - Add "Electronics" or "White Goods"

2. **Add a Warehouse**
   - Go to Warehouses
   - Create "Main Warehouse"
   - Set location and details

3. **Add Products**
   - Navigate to Products
   - Create products with SKU and category
   - Set pricing and attributes

4. **Manage Stock**
   - Go to Stock section
   - Link products to warehouses
   - Set initial quantities

---

## Database Configuration

### H2 (Development - Default)

```properties
JDBC URL: jdbc:h2:mem:warehouse_db
Username: sa
Password: password
```

### PostgreSQL (Production)

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/warehouse_db
SPRING_DATASOURCE_USERNAME=your_username
SPRING_DATASOURCE_PASSWORD=your_password
SPRING_PROFILES_ACTIVE=prod
```

---

## Common Commands

```bash
# Stop services
docker-compose down

# Rebuild
docker-compose build --no-cache

# View logs
docker-compose logs backend
docker-compose logs frontend

# Remove all data
docker-compose down -v
```

---

## Troubleshooting

### Port Already in Use

```bash
# Check what's using the port
lsof -i :8080

# Change port in docker-compose.yml
ports:
  - "8081:8080"
```

### Build Errors

```bash
# Clean rebuild
mvn clean
docker-compose build --no-cache
docker-compose up -d
```

### Database Connection Issues

```bash
# Check logs
docker-compose logs backend

# Verify database is running
docker-compose ps
```

---

## Testing

```bash
# Run backend tests
mvn test

# Run frontend tests
cd frontend
npm test
```

---

## Next Steps

- Explore the [Main README](README.md)
- Check out the [Contributing Guidelines](CONTRIBUTING.md)

---

## Need Help?

- **Issues:** [GitHub Issues](https://github.com/yourusername/warehouse-management/issues)
- **Documentation:** See README.md for complete documentation
- **Email:** support@example.com

---

**🎉 You're all set! Happy warehouse managing!**
