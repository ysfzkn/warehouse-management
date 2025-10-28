<div align="center">

# 🎨 Warehouse Management - Frontend

**Modern, responsive React application for warehouse management**

[![React](https://img.shields.io/badge/React-18.2.0-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://reactjs.org/)
[![Bootstrap](https://img.shields.io/badge/Bootstrap-5.2.3-7952B3?style=for-the-badge&logo=bootstrap&logoColor=white)](https://getbootstrap.com/)
[![Chart.js](https://img.shields.io/badge/Chart.js-4.3.0-FF6384?style=for-the-badge&logo=chart.js&logoColor=white)](https://www.chartjs.org/)

</div>

---

## 📱 Features

- ✨ **Modern UI** - Clean, intuitive interface built with Bootstrap 5
- 📊 **Real-time Dashboard** - Live statistics and interactive charts
- 📱 **Fully Responsive** - Optimized for mobile, tablet, and desktop
- 🎯 **Component-Based** - Reusable React components
- ⚡ **Fast Performance** - Optimized rendering and lazy loading
- 🔄 **Live Updates** - Real-time data synchronization
- 🎨 **Professional Design** - Modern UX with attention to detail

---

## 🚀 Quick Start

### Prerequisites

- Node.js 18+ 
- npm or yarn

### Installation

```bash
# Install dependencies
npm install

# Start development server
npm start

# Build for production
npm run build
```

Application opens at **http://localhost:3000**

---

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| **React** | 18.2.0 | UI Framework |
| **React Router** | 6.11.0 | Client-side routing |
| **Bootstrap** | 5.2.3 | CSS Framework |
| **React Bootstrap** | 2.7.4 | Bootstrap Components |
| **Axios** | 1.4.0 | HTTP Client |
| **Chart.js** | 4.3.0 | Data Visualization |
| **React Icons** | 4.10.1 | Icon Library |

---

## 📁 Project Structure

```
frontend/
├── public/
│   ├── index.html              # HTML template
│   └── sahinler-logo.png       # App logo
│
├── src/
│   ├── components/             # Reusable components
│   │   ├── Navbar.js           # Navigation bar
│   │   ├── ProductForm.js      # Product create/edit form
│   │   ├── StockForm.js        # Stock management form
│   │   ├── WarehouseForm.js    # Warehouse form
│   │   ├── CategoryForm.js     # Category form
│   │   ├── StockModal.js       # Stock details modal
│   │   ├── StockAdjustmentModal.js  # Stock adjustment
│   │   ├── StockTransferModal.js    # Transfer between warehouses
│   │   ├── ConfirmModal.js     # Confirmation dialogs
│   │   ├── NotesModal.js       # Notes display
│   │   ├── FilterChips.js      # Active filter chips
│   │   └── SearchableSelect.js # Searchable dropdown
│   │
│   ├── pages/                  # Page components
│   │   ├── Login.js            # Login page
│   │   ├── Dashboard.js        # Main dashboard
│   │   ├── Products.js         # Product management
│   │   ├── Stock.js            # Stock management
│   │   ├── Warehouses.js       # Warehouse management
│   │   ├── Categories.js       # Category management
│   │   ├── AdminSettings.js    # Admin settings
│   │   └── DesiCalculator.js   # Desi calculator tool
│   │
│   ├── App.js                  # Main app component
│   ├── config.js               # API configuration
│   ├── App.css                 # Global styles
│   └── index.js                # React entry point
│
├── package.json                # Dependencies
├── Dockerfile                  # Docker config
└── nginx.conf                  # Nginx proxy
```

---

## 🎨 Key Components

### Dashboard
- Overview statistics (products, categories, warehouses, stock)
- Low stock alerts
- Interactive charts (Pie, Bar)
- Quick action buttons

### Product Management
- Product listing with search
- Create/Edit/Delete products
- Category, brand, and color filtering
- SKU management
- Product activation/deactivation

### Stock Management
- Real-time stock levels
- Multi-warehouse inventory
- Stock adjustments (add/remove)
- Stock transfers between warehouses
- Reserved and consigned quantities
- Low stock alerts

### Warehouse Management
- Warehouse CRUD operations
- Capacity tracking
- Stock overview per warehouse
- Warehouse activation/deactivation

### Transfer System
- Transfer creation between warehouses
- Transfer status tracking (Pending → In Transit → Completed)
- Driver and vehicle information
- Transfer cancellation
- Transfer history

---

## ⚙️ Configuration

### API Configuration

Edit `src/config.js`:

```javascript
const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

export const API_URL = `${API_BASE_URL}/api`;
```

### Environment Variables

Create `.env` file:

```env
REACT_APP_API_URL=http://localhost:8080
```

### Proxy Setup

In `package.json`:

```json
{
  "proxy": "http://localhost:8080"
}
```

---

## 📱 Responsive Design

### Breakpoints

- **Mobile**: < 576px
- **Tablet**: 576px - 768px  
- **Desktop**: 768px - 992px
- **Large Desktop**: > 992px

### Mobile Features

- Touch-optimized buttons
- Collapsible navigation
- Responsive tables with horizontal scroll
- Optimized modals for small screens
- Mobile-first forms

---

## 🎯 Component Examples

### Using Forms

```jsx
import ProductForm from './components/ProductForm';

function ProductsPage() {
  const handleSuccess = () => {
    fetchProducts(); // Refresh list
  };

  return (
    <ProductForm
      product={editingProduct}
      categories={categories}
      brands={brands}
      colors={colors}
      onSuccess={handleSuccess}
      onCancel={() => setShowForm(false)}
    />
  );
}
```

### API Calls

```jsx
import axios from 'axios';
import { API_URL } from './config';

const fetchProducts = async () => {
  try {
    const response = await axios.get(`${API_URL}/products`, {
      headers: {
        'Authorization': `Basic ${btoa('admin:admin')}`
      }
    });
    setProducts(response.data);
  } catch (error) {
    console.error('Error:', error);
  }
};
```

### Charts

```jsx
import { Pie, Bar } from 'react-chartjs-2';

const chartData = {
  labels: ['In Stock', 'Low Stock', 'Out of Stock'],
  datasets: [{
    data: [120, 30, 5],
    backgroundColor: ['#28a745', '#ffc107', '#dc3545']
  }]
};

<Pie data={chartData} options={chartOptions} />
```

---

## 🚀 Development

### Available Scripts

```bash
# Start dev server (http://localhost:3000)
npm start

# Run tests
npm test

# Build for production
npm run build

# Eject config (not recommended)
npm run eject
```

### Development Tips

- Hot reload is enabled by default
- Use React DevTools extension for debugging
- Check Network tab for API call issues
- Use `console.log()` for quick debugging

---

## 🏗️ Build & Deployment

### Production Build

```bash
npm run build
```

Creates optimized build in `build/` folder:
- Minified JavaScript
- Optimized CSS
- Compressed assets
- Service worker for caching

### Docker Deployment

```bash
# Build image
docker build -t warehouse-frontend .

# Run container
docker run -p 80:80 warehouse-frontend
```

### Static Hosting

Deploy `build/` folder to:
- Netlify
- Vercel
- AWS S3 + CloudFront
- GitHub Pages
- Any static hosting

---

## 🎨 Styling

### Bootstrap Classes

```jsx
<div className="container">
  <div className="row">
    <div className="col-md-6 col-lg-4">
      <div className="card shadow-sm">
        {/* Content */}
      </div>
    </div>
  </div>
</div>
```

### Custom Styles

Global styles in `App.css` and `index.css`

---

## 🧪 Testing

```bash
# Run tests
npm test

# Run tests with coverage
npm test -- --coverage

# Run tests in watch mode
npm test -- --watch
```

---

## 🔍 Troubleshooting

### Common Issues

**Port already in use:**
```bash
# Kill process on port 3000
npx kill-port 3000
# Or change port
PORT=3001 npm start
```

**Build errors:**
```bash
# Clean install
rm -rf node_modules package-lock.json
npm install
```

**API connection errors:**
- Ensure backend is running on port 8080
- Check CORS settings
- Verify Authorization header
- Check browser console for errors

**Slow performance:**
- Clear browser cache
- Check for console errors
- Optimize images
- Use React.memo for expensive components

---

## 🤝 Contributing

1. Create feature branch
2. Make changes
3. Test thoroughly
4. Submit pull request

See [CONTRIBUTING.md](../CONTRIBUTING.md) for detailed guidelines.

---

## 📄 License

MIT License - see [LICENSE](../LICENSE) file for details.

---

## 🆘 Support

- **Main Docs**: [../README.md](../README.md)
- **API Docs**: [../API_README.md](../API_README.md)
- **Quick Start**: [../QUICK_START.md](../QUICK_START.md)

---

<div align="center">

**Built with ❤️ using React**

[⬆ Back to Top](#-warehouse-management---frontend)

</div>
