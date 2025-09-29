# Warehouse Management System - Frontend

React-based, mobile-friendly warehouse management system interface.

## 🚀 Features

- **Modern React 18** - Uses latest React features
- **Responsive Design** - Compatible with mobile, tablet, and desktop
- **Bootstrap 5** - Modern and elegant UI components
- **Chart.js Integration** - Charts for dashboard
- **Real-time Updates** - Instant data updates
- **Form Validation** - Client-side validation
- **Error Handling** - Comprehensive error management

## 📋 Requirements

- **Node.js 18+**
- **npm** or **yarn**
- **Modern web browser**

## 🛠️ Installation

### 1. Install Dependencies

```bash
npm install
```

### 2. Start Development Server

```bash
npm start
```

The application will open at http://localhost:3000.

### 3. Production Build

```bash
npm run build
```

Build files will be created in the `build/` folder.

## 📁 Project Structure

```
frontend/
├── public/
│   └── index.html          # Main HTML file
├── src/
│   ├── components/         # Reusable components
│   │   ├── Navbar.js       # Navigation bar
│   │   ├── WarehouseForm.js # Warehouse form
│   │   ├── ProductForm.js   # Product form
│   │   ├── CategoryForm.js  # Category form
│   │   ├── StockForm.js     # Stock form
│   │   ├── StockModal.js    # Stock viewing modal
│   │   └── StockAdjustmentModal.js # Stock adjustment modal
│   ├── pages/              # Page components
│   │   ├── Dashboard.js     # Main dashboard
│   │   ├── Warehouses.js    # Warehouse management
│   │   ├── Products.js      # Product management
│   │   ├── Categories.js    # Category management
│   │   └── Stock.js         # Stock management
│   ├── App.js              # Main application component
│   ├── index.js            # React DOM entry point
│   ├── App.css             # Main style file
│   └── index.css           # Global styles
├── package.json            # Project dependencies and scripts
├── Dockerfile              # Docker configuration
└── nginx.conf              # Nginx proxy configuration
```

## 🎨 Technologies Used

### Core Technologies
- **React 18.2.0** - UI framework
- **React Router DOM 6.11.0** - Page routing
- **Axios 1.4.0** - HTTP client
- **Bootstrap 5.2.3** - CSS framework
- **React Bootstrap 2.7.4** - Bootstrap React components

### Additional Libraries
- **Chart.js 4.3.0** - Charts and graphs
- **React Chart.js 2 5.2.0** - Chart.js React wrapper
- **React Icons 4.10.1** - Icon library
- **React Scripts 5.0.1** - Build tools

## 🔧 Configuration

### Environment Variables

You can configure the API URL by creating a `.env` file:

```env
REACT_APP_API_URL=http://localhost:8080/api
```

### Proxy Configuration

Proxy configuration in `package.json`:

```json
{
  "proxy": "http://localhost:8080"
}
```

This automatically redirects `/api` endpoints to the backend.

## 🚀 Development

### Development Server

```bash
npm start
```

- Hot reload enabled
- Error overlay
- http://localhost:3000

### Code Quality

```bash
# Lint code
npm run lint

# Run tests
npm test

# Build production
npm run build
```

## 📱 Responsive Design

### Breakpoints

- **Mobile**: < 576px
- **Tablet**: 576px - 768px
- **Desktop**: > 768px

### Mobile Optimizations

- Touch-friendly buttons
- Responsive tables
- Collapsible navigation
- Optimized form layouts

## 🎯 Component Usage

### Navbar

Main navigation component:

```jsx
import Navbar from './components/Navbar';

function App() {
  return (
    <div>
      <Navbar />
      {/* Page content */}
    </div>
  );
}
```

### Form Components

Form components come with validation and error handling:

```jsx
import ProductForm from './components/ProductForm';

function ProductsPage() {
  const handleSuccess = () => {
    // When form is successfully submitted
    console.log('Product saved');
  };

  return (
    <ProductForm
      product={editingProduct}
      categories={categories}
      onSuccess={handleSuccess}
      onCancel={() => setShowForm(false)}
    />
  );
}
```

### API Integration

API calls with Axios:

```jsx
import axios from 'axios';

const fetchProducts = async () => {
  try {
    const response = await axios.get('/api/products');
    setProducts(response.data);
  } catch (error) {
    console.error('Error loading products:', error);
    setError('Failed to load products');
  }
};
```

## 🔍 State Management

Uses React useState and useEffect hooks:

```jsx
const [products, setProducts] = useState([]);
const [loading, setLoading] = useState(true);
const [error, setError] = useState(null);

useEffect(() => {
  fetchProducts();
}, []);
```

## 🎨 Styling

### CSS Modules

Component-based style files:

```css
/* ProductForm.module.css */
.formContainer {
  max-width: 600px;
  margin: 0 auto;
}

.error {
  color: #dc3545;
  font-size: 0.875rem;
}
```

### Bootstrap Utilities

Responsive grid system:

```jsx
<div className="row">
  <div className="col-md-6 col-lg-4">
    {/* Content */}
  </div>
</div>
```

## 📊 Dashboard Components

### Charts

Chart.js integration:

```jsx
import { Bar, Pie } from 'react-chartjs-2';

const chartData = {
  labels: ['Warehouses', 'Products', 'Categories'],
  datasets: [{
    data: [5, 150, 10],
    backgroundColor: ['#007bff', '#28a745', '#ffc107']
  }]
};

<Pie data={chartData} />
```

## 🔒 Error Handling

Global error handling:

```jsx
const handleApiError = (error) => {
  if (error.response) {
    // Server response error
    setError(error.response.data.message);
  } else if (error.request) {
    // Network error
    setError('Network connection error');
  } else {
    // Other error
    setError('Unknown error');
  }
};
```

## 🚀 Deployment

### With Docker

```bash
# Build and run
docker build -t warehouse-frontend .
docker run -p 80:80 warehouse-frontend
```

### Static Hosting

You can upload build files to any static hosting:

```bash
npm run build
# Upload build/ folder to hosting
```

### Nginx Configuration

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080;
    }
}
```

## 🧪 Testing

### Unit Tests

```bash
npm test
```

### Test Coverage

```bash
npm test -- --coverage
```

### E2E Tests

Cypress or Playwright can be added.

## 📝 Code Style

### ESLint

```bash
npm run lint
```

### Prettier

Prettier is used for code formatting.

## 🤝 Contributing

1. Create a feature branch
2. Make changes
3. Test them
4. Open a pull request

## 📄 License

MIT License - see [LICENSE](../LICENSE) file for details.

## 🆘 Troubleshooting

### Common Issues

**Build Error:**
```bash
rm -rf node_modules package-lock.json
npm install
```

**Port Already in Use:**
```bash
npx kill-port 3000
```

**API Connection Error:**
- Make sure backend is running
- Check CORS settings
- Inspect API calls in Network tab

### Debug Mode

```jsx
// Add console logging
console.log('API Response:', response.data);

// Use React DevTools
// Inspect Network tab
```

## 📞 Support

If you encounter any issues:

1. Check console errors
2. Inspect API calls in Network tab
3. Use Browser DevTools
4. Open an issue

## 🗺️ Roadmap

- [ ] Dark mode support
- [ ] Offline PWA support
- [ ] Advanced filtering
- [ ] Drag & drop upload
- [ ] Real-time notifications
- [ ] Mobile app (React Native)
- [ ] Advanced charts
- [ ] Export functionality
