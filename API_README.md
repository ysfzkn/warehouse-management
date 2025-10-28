# 📖 API Documentation

Complete REST API documentation for the Warehouse Management System.

**Base URL:** `http://localhost:8080/api` (development)  
**Production:** `https://your-domain.com/api`

---

## 🔐 Authentication

Currently, the API uses Basic Authentication:

```http
Authorization: Basic YWRtaW46YWRtaW4=
```

**Default Credentials:**
- Username: `admin`
- Password: `admin`

> **Note:** Update credentials in production via environment variables `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD`

---

## 📝 Response Format

### Success Response

```json
{
  "id": 1,
  "name": "Product Name",
  "sku": "PROD-001",
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:30:00"
}
```

### Error Response

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

### Validation Error

```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "Input validation failed",
  "fieldErrors": {
    "name": "Product name is required",
    "sku": "SKU must be unique"
  },
  "path": "/api/products",
  "timestamp": "2024-01-15T10:30:00"
}
```

---

## 📦 Products API

### List All Products

```http
GET /api/products
```

**Response:** `200 OK`

```json
[
  {
    "id": 1,
    "name": "Industrial Refrigerator",
    "sku": "REF-2024-001",
    "price": 15000.00,
    "weight": 120.5,
    "dimensions": "80x80x200 cm",
    "category": { "id": 1, "name": "White Goods" },
    "brand": { "id": 2, "name": "Samsung" },
    "color": { "id": 3, "name": "Silver" },
    "isActive": true,
    "createdAt": "2024-01-15T10:00:00",
    "updatedAt": "2024-01-15T10:00:00"
  }
]
```

### Get Product by ID

```http
GET /api/products/{id}
```

**Response:** `200 OK` or `404 Not Found`

### Create Product

```http
POST /api/products
Content-Type: application/json

{
  "name": "Industrial Refrigerator",
  "sku": "REF-2024-001",
  "price": 15000.00,
  "weight": 120.5,
  "dimensions": "80x80x200 cm",
  "description": "Energy-efficient industrial refrigerator",
  "category": { "id": 1 },
  "brand": { "id": 2 },
  "color": { "id": 3 }
}
```

**Validation Rules:**
- `name`: Required, 2-100 characters
- `sku`: Required, 3-50 characters, unique
- `price`: Required, must be positive
- `category`: Required, must exist

**Response:** `201 Created` or `400 Bad Request` or `409 Conflict`

### Update Product

```http
PUT /api/products/{id}
Content-Type: application/json

{
  "name": "Updated Name",
  "price": 17500.00
}
```

**Response:** `200 OK` or `404 Not Found`

### Delete Product

```http
DELETE /api/products/{id}
```

**Response:** `204 No Content` or `404 Not Found`

### Additional Endpoints

```http
GET  /api/products/active                    # List active products
GET  /api/products/sku/{sku}                 # Find by SKU
GET  /api/products/category/{categoryId}     # Filter by category
GET  /api/products/search?name={keyword}     # Search by name
PUT  /api/products/{id}/activate             # Activate product
PUT  /api/products/{id}/deactivate           # Deactivate product
GET  /api/products?brandId={id}&colorId={id} # Filter by brand/color
```

---

## 📁 Categories API

### List All Categories

```http
GET /api/categories
```

### Create Category

```http
POST /api/categories
Content-Type: application/json

{
  "name": "Electronics",
  "description": "Electronic devices and appliances"
}
```

**Validation:**
- `name`: Required, 2-50 characters, unique

### Additional Endpoints

```http
GET    /api/categories/{id}                    # Get category
PUT    /api/categories/{id}                    # Update category
DELETE /api/categories/{id}                    # Delete category (no products)
GET    /api/categories/{id}/with-products      # Category with products
GET    /api/categories/with-product-count      # Categories with count
```

---

## 🏭 Warehouses API

### List All Warehouses

```http
GET /api/warehouses
```

### Create Warehouse

```http
POST /api/warehouses
Content-Type: application/json

{
  "name": "Main Warehouse",
  "location": "New York, USA",
  "phone": "+1-555-0100",
  "manager": "John Doe",
  "capacitySqm": 5000.0,
  "isActive": true
}
```

**Validation:**
- `name`: Required, 2-100 characters, unique
- `location`: Required, 5-255 characters

### Additional Endpoints

```http
GET  /api/warehouses/{id}                # Get warehouse
PUT  /api/warehouses/{id}                # Update warehouse
DELETE /api/warehouses/{id}              # Delete warehouse (no stocks)
GET  /api/warehouses/active              # Active warehouses
PUT  /api/warehouses/{id}/activate       # Activate warehouse
PUT  /api/warehouses/{id}/deactivate     # Deactivate warehouse
GET  /api/warehouses/{id}/with-stocks    # Warehouse with stocks
```

---

## 📊 Stocks API

### List All Stocks

```http
GET /api/stocks
```

### Create Stock Record

```http
POST /api/stocks
Content-Type: application/json

{
  "product": { "id": 1 },
  "warehouse": { "id": 1 },
  "quantity": 100,
  "minStockLevel": 10,
  "reservedQuantity": 0,
  "consignedQuantity": 0
}
```

**Validation:**
- `product`: Required, must exist
- `warehouse`: Required, must exist
- `quantity`: Required, must be >= 0
- Unique constraint: (product_id, warehouse_id)

### Update Stock

```http
PUT /api/stocks/{id}
Content-Type: application/json

{
  "quantity": 150,
  "minStockLevel": 15,
  "reservedQuantity": 10
}
```

### Stock Operations

```http
PUT /api/stocks/{id}/add?quantity=50        # Add to stock
PUT /api/stocks/{id}/remove?quantity=25     # Remove from stock
PUT /api/stocks/{id}/reserve?quantity=10    # Reserve stock
PUT /api/stocks/{id}/release?quantity=5     # Release reservation
```

### Additional Endpoints

```http
GET /api/stocks/product/{productId}                              # Stocks by product
GET /api/stocks/warehouse/{warehouseId}                          # Stocks by warehouse
GET /api/stocks/product/{pid}/warehouse/{wid}                    # Specific stock
GET /api/stocks/low-stock                                        # Low stock alerts
GET /api/stocks/out-of-stock                                     # Out of stock
GET /api/stocks/warehouse/{warehouseId}/low-stock                # Low stock by warehouse
GET /api/stocks/product/{productId}/total-quantity               # Total quantity
GET /api/stocks?brandId={id}&colorId={id}&warehouseId={id}      # Filter stocks
```

---

## 🔄 Stock Transfers API

### List All Transfers

```http
GET /api/stock-transfers
```

### Create Transfer

```http
POST /api/stock-transfers
Content-Type: application/json

{
  "sourceWarehouse": { "id": 1 },
  "destinationWarehouse": { "id": 2 },
  "product": { "id": 1 },
  "quantity": 50,
  "driverName": "Mike Johnson",
  "driverTcId": "12345678901",
  "driverPhone": "+1-555-0200",
  "vehiclePlate": "ABC-1234",
  "notes": "Urgent transfer"
}
```

**Validation:**
- Source and destination warehouses must be different
- Sufficient stock must be available in source warehouse
- `quantity`: Required, must be positive

**Transfer Statuses:**
- `PENDING` - Transfer created, not started
- `IN_TRANSIT` - Transfer in progress, stock reserved
- `COMPLETED` - Transfer completed, stock moved
- `CANCELLED` - Transfer cancelled

### Transfer Operations

```http
PUT /api/stock-transfers/{id}/start      # Start transfer (PENDING → IN_TRANSIT)
PUT /api/stock-transfers/{id}/complete   # Complete transfer (→ COMPLETED)
PUT /api/stock-transfers/{id}/cancel     # Cancel transfer (→ CANCELLED)
PUT /api/stock-transfers/{id}            # Update transfer (PENDING only)
```

### Additional Endpoints

```http
GET /api/stock-transfers/{id}                     # Get transfer
GET /api/stock-transfers/warehouse/{id}           # Transfers by warehouse
GET /api/stock-transfers/product/{id}             # Transfers by product
GET /api/stock-transfers/status/{status}          # Transfers by status
DELETE /api/stock-transfers/{id}                  # Delete (PENDING/CANCELLED only)
```

---

## 🏷️ Brands API

```http
GET    /api/brands              # List all brands
POST   /api/brands              # Create brand
GET    /api/brands/{id}         # Get brand
PUT    /api/brands/{id}         # Update brand
DELETE /api/brands/{id}         # Delete brand (no products)
```

**Create Brand:**
```json
{
  "name": "Samsung"
}
```

---

## 🎨 Colors API

```http
GET    /api/colors              # List all colors
POST   /api/colors              # Create color
GET    /api/colors/{id}         # Get color
PUT    /api/colors/{id}         # Update color
DELETE /api/colors/{id}         # Delete color (no products)
```

**Create Color:**
```json
{
  "name": "Silver",
  "hexCode": "#C0C0C0"
}
```

---

## ℹ️ System Info API

```http
GET /api/info                   # System information
GET /actuator/health            # Health check
```

---

## 📋 Error Codes

### Product Errors

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `PRODUCT_001` | 404 | Product not found |
| `PRODUCT_002` | 409 | SKU already exists |

### Category Errors

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `CATEGORY_001` | 404 | Category not found |
| `CATEGORY_002` | 409 | Category name already exists |

### Warehouse Errors

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `WAREHOUSE_001` | 404 | Warehouse not found |
| `WAREHOUSE_002` | 409 | Warehouse name already exists |

### Stock Errors

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `STOCK_001` | 404 | Stock not found |
| `STOCK_002` | 409 | Stock already exists for this product/warehouse |
| `STOCK_003` | 400 | Insufficient stock |
| `STOCK_004` | 400 | Insufficient reserved stock |
| `STOCK_005` | 400 | Product not found in warehouse |

### Transfer Errors

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `TRANSFER_001` | 404 | Transfer not found |
| `TRANSFER_002` | 400 | Source and destination must be different |
| `TRANSFER_004` | 400 | Transfer already completed |
| `TRANSFER_007` | 400 | Cannot delete transfer in transit |
| `TRANSFER_009` | 400 | Only pending transfers can be updated |

### Validation Errors

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `VALIDATION_001` | 400 | Required field missing |
| `VALIDATION_003` | 400 | Value must be positive |
| `VALIDATION_004` | 400 | Value cannot be negative |

---

## 🧪 Testing with cURL

### Create Product

```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Basic YWRtaW46YWRtaW4=" \
  -d '{
    "name": "Test Product",
    "sku": "TEST-001",
    "price": 100.00,
    "category": {"id": 1}
  }'
```

### Get All Products

```bash
curl -X GET http://localhost:8080/api/products \
  -H "Authorization: Basic YWRtaW46YWRtaW4="
```

### Create Stock Transfer

```bash
curl -X POST http://localhost:8080/api/stock-transfers \
  -H "Content-Type: application/json" \
  -H "Authorization: Basic YWRtaW46YWRtaW4=" \
  -d '{
    "sourceWarehouse": {"id": 1},
    "destinationWarehouse": {"id": 2},
    "product": {"id": 1},
    "quantity": 10
  }'
```

---

## 📦 JavaScript/Axios Examples

### Setup

```javascript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  auth: {
    username: 'admin',
    password: 'admin'
  }
});
```

### Get Products

```javascript
const getProducts = async () => {
  try {
    const response = await api.get('/products');
    console.log(response.data);
  } catch (error) {
    console.error('Error:', error.response.data);
  }
};
```

### Create Product

```javascript
const createProduct = async (productData) => {
  try {
    const response = await api.post('/products', productData);
    console.log('Created:', response.data);
  } catch (error) {
    if (error.response.data.errorCode === 'PRODUCT_002') {
      console.error('SKU already exists');
    }
  }
};
```

---

## 🔒 CORS Configuration

The API supports CORS for the following:

- **Allowed Origins:** `*` (configure for production)
- **Allowed Methods:** GET, POST, PUT, DELETE, OPTIONS
- **Allowed Headers:** Content-Type, Authorization

---

## 📊 Rate Limiting

Currently, no rate limiting is implemented. Consider adding rate limiting for production:

- Use Spring Cloud Gateway
- Implement Bucket4j
- Use API Gateway (AWS, Kong, etc.)

---

## 🚀 Production Considerations

1. **Change default credentials**
2. **Enable HTTPS only**
3. **Configure CORS properly**
4. **Add API rate limiting**
5. **Enable request logging**
6. **Use database connection pooling**
7. **Configure proper error logging**

---

For more information, see the [Main README](README.md) or [Quick Start Guide](QUICK_START.md).
