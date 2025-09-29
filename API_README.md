# Warehouse Management System API Documentation

This documentation provides detailed information about the RESTful API endpoints of the Warehouse Management System.

Base URL: `http://localhost:8080/api`

## Authentication

Currently, there is no authentication system implemented. All endpoints are public.

## Response Format

All API responses are returned in JSON format:

```json
{
  "success": true,
  "data": { ... },
  "message": "Operation completed successfully"
}
```

In error cases:

```json
{
  "success": false,
  "error": "Error message",
  "details": "Detailed error information"
}
```

## Categories API

Endpoints for category management.

### List All Categories

```http
GET /api/categories
```

**Response:**
```json
[
  {
    "id": 1,
    "name": "White Goods",
    "description": "Refrigerator, washing machine, etc.",
    "createdAt": "2024-01-01T10:00:00",
    "updatedAt": "2024-01-01T10:00:00",
    "products": []
  }
]
```

### List Active Categories

```http
GET /api/categories/active
```

### Get Category Details

```http
GET /api/categories/{id}
```

### List Category with Products

```http
GET /api/categories/{id}/with-products
```

### Create New Category

```http
POST /api/categories
Content-Type: application/json

{
  "name": "Electronics",
  "description": "TV, computer, phone, etc."
}
```

**Validation:**
- `name`: Required, 2-50 characters, unique

### Update Category

```http
PUT /api/categories/{id}
Content-Type: application/json

{
  "name": "New Category Name",
  "description": "Updated description"
}
```

### Delete Category

```http
DELETE /api/categories/{id}
```

**Note:** Categories containing products cannot be deleted.

---

## Warehouses API

Endpoints for warehouse management.

### List All Warehouses

```http
GET /api/warehouses
```

### List Active Warehouses

```http
GET /api/warehouses/active
```

### Get Warehouse Details

```http
GET /api/warehouses/{id}
```

### List Warehouse with Stocks

```http
GET /api/warehouses/{id}/with-stocks
```

### Create New Warehouse

```http
POST /api/warehouses
Content-Type: application/json

{
  "name": "Main Warehouse",
  "location": "Istanbul, Turkey",
  "phone": "0212 555 0000",
  "manager": "Ahmet Yılmaz",
  "capacitySqm": 1000.5,
  "isActive": true
}
```

**Validation:**
- `name`: Required, 2-100 characters, unique
- `location`: Required, 5-255 characters

### Update Warehouse

```http
PUT /api/warehouses/{id}
Content-Type: application/json

{
  "name": "Updated Warehouse Name",
  "location": "Ankara, Turkey"
}
```

### Delete Warehouse

```http
DELETE /api/warehouses/{id}
```

### Activate Warehouse

```http
PUT /api/warehouses/{id}/activate
```

### Deactivate Warehouse

```http
PUT /api/warehouses/{id}/deactivate
```

---

## Products API

Endpoints for product management.

### List All Products

```http
GET /api/products
```

### List Active Products

```http
GET /api/products/active
```

### Get Product Details

```http
GET /api/products/{id}
```

### List Product with Stocks

```http
GET /api/products/{id}/with-stocks
```

### Find Product by SKU

```http
GET /api/products/sku/{sku}
```

### Get Products by Category

```http
GET /api/products/category/{categoryId}
```

### Search Products

```http
GET /api/products/search?name=refrigerator
```

### Create New Product

```http
POST /api/products
Content-Type: application/json

{
  "name": "Samsung Refrigerator",
  "description": "A++ energy class",
  "sku": "REF-001",
  "price": 15000.00,
  "weight": 80.5,
  "dimensions": "60x60x180 cm",
  "category": {
    "id": 1
  },
  "isActive": true
}
```

**Validation:**
- `name`: Required, 2-100 characters
- `sku`: Required, 3-50 characters, unique
- `price`: Required, must be positive
- `category`: Required, must exist

### Update Product

```http
PUT /api/products/{id}
Content-Type: application/json

{
  "name": "Updated Product Name",
  "price": 17500.00
}
```

### Delete Product

```http
DELETE /api/products/{id}
```

### Activate Product

```http
PUT /api/products/{id}/activate
```

### Deactivate Product

```http
PUT /api/products/{id}/deactivate
```

---

## Stocks API

Endpoints for stock management.

### List All Stocks

```http
GET /api/stocks
```

### Get Stock Details

```http
GET /api/stocks/{id}
```

### Get Stocks by Product

```http
GET /api/stocks/product/{productId}
```

### Get Stocks by Warehouse

```http
GET /api/stocks/warehouse/{warehouseId}
```

### Get Stock by Product-Warehouse Combination

```http
GET /api/stocks/product/{productId}/warehouse/{warehouseId}
```

### Get Low Stock Products

```http
GET /api/stocks/low-stock
```

### Get Out of Stock Products

```http
GET /api/stocks/out-of-stock
```

### Get Low Stock by Warehouse

```http
GET /api/stocks/warehouse/{warehouseId}/low-stock
```

### Get Total Quantity by Product

```http
GET /api/stocks/product/{productId}/total-quantity
```

### Get Total Quantity by Warehouse

```http
GET /api/stocks/warehouse/{warehouseId}/total-quantity
```

### Create New Stock Record

```http
POST /api/stocks
Content-Type: application/json

{
  "product": {
    "id": 1
  },
  "warehouse": {
    "id": 1
  },
  "quantity": 100,
  "minStockLevel": 10,
  "reservedQuantity": 0
}
```

**Validation:**
- `product`: Required, must exist
- `warehouse`: Required, must exist
- `quantity`: Required, must be >= 0
- `minStockLevel`: Required, must be >= 0

### Update Stock

```http
PUT /api/stocks/{id}
Content-Type: application/json

{
  "quantity": 150,
  "minStockLevel": 15,
  "reservedQuantity": 5
}
```

### Add to Stock

```http
PUT /api/stocks/{id}/add?quantity=50
```

### Remove from Stock

```http
PUT /api/stocks/{id}/remove?quantity=25
```

### Reserve Stock

```http
PUT /api/stocks/{id}/reserve?quantity=10
```

### Release Reservation

```http
PUT /api/stocks/{id}/release?quantity=5
```

### Delete Stock Record

```http
DELETE /api/stocks/{id}
```

---

## Error Handling

### Common HTTP Status Codes

- `200 OK`: Successful request
- `201 Created`: Successful creation
- `204 No Content`: Successful deletion
- `400 Bad Request`: Invalid request (validation error)
- `404 Not Found`: Resource not found
- `409 Conflict`: Conflict (unique constraint violation)
- `500 Internal Server Error`: Server error

### Error Response Examples

**Validation Error:**
```json
{
  "success": false,
  "error": "Validation failed",
  "details": {
    "name": "Product name is required",
    "sku": "SKU is required"
  }
}
```

**Not Found Error:**
```json
{
  "success": false,
  "error": "Product not found with id: 999"
}
```

**Conflict Error:**
```json
{
  "success": false,
  "error": "Product with SKU 'REF-001' already exists"
}
```

---

## Rate Limiting

Currently, there is no rate limiting implementation. Should be added in production environment.

## CORS

The API is configured for Cross-Origin Resource Sharing (CORS):

- `Access-Control-Allow-Origin`: *
- `Access-Control-Allow-Methods`: GET, POST, PUT, DELETE, OPTIONS
- `Access-Control-Allow-Headers`: Content-Type, Authorization

## Pagination

Pagination support for large datasets has not been added yet. Can be added when needed.

## Filtering & Sorting

### Query Parameters

Most list endpoints support these query parameters:

- `?page=0&size=20`: Pagination
- `?sort=name,asc`: Sorting
- `?name=filter`: Filtering by name

## Versioning

API versioning is not currently implemented with URL paths. May switch to `/api/v1/` format in the future.

---

## Example Usage

### JavaScript (Axios) API Calls

```javascript
import axios from 'axios';

// List all products
const getProducts = async () => {
  try {
    const response = await axios.get('/api/products');
    console.log(response.data);
  } catch (error) {
    console.error('Error:', error.response?.data);
  }
};

// Create new product
const createProduct = async (productData) => {
  try {
    const response = await axios.post('/api/products', productData);
    console.log('Created:', response.data);
  } catch (error) {
    console.error('Error:', error.response?.data);
  }
};

// Update product
const updateProduct = async (id, productData) => {
  try {
    const response = await axios.put(`/api/products/${id}`, productData);
    console.log('Updated:', response.data);
  } catch (error) {
    console.error('Error:', error.response?.data);
  }
};
```

### cURL API Testing

```bash
# List all categories
curl -X GET http://localhost:8080/api/categories

# Create new category
curl -X POST http://localhost:8080/api/categories \
  -H "Content-Type: application/json" \
  -d '{"name": "New Category", "description": "Description"}'

# Search products
curl -X GET "http://localhost:8080/api/products/search?name=refrigerator"
```

This documentation is prepared for the current state of the API. It will be updated as new features are added.
