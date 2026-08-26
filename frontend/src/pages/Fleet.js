import React, { useState } from 'react';
import { useAdminToast } from '../components/AdminToast';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';
import Drivers from './Drivers';
import Vehicles from './Vehicles';

/**
 * Drivers and vehicles under one roof.
 *
 * They are two directories, not one — a driver takes a different vehicle depending on the day —
 * but they are always managed in the same sitting, so they share a screen and a pair of tabs
 * rather than two separate menu entries.
 */
export default function Fleet() {
  const toast = useAdminToast();
  const { askCode, SecurityCodePrompt } = useSecurityCodePrompt();
  const [tab, setTab] = useState('drivers');

  const role = (typeof window !== 'undefined' && localStorage.getItem('auth_role')) || 'ADMIN';
  const canManage = role === 'ADMIN';

  return (
    <div>
      {SecurityCodePrompt}

      <div className="mb-3">
        <h4 className="fw-bold mb-1">
          <i className="fas fa-truck-fast text-primary me-2" />
          Şoförler ve Araçlar
        </h4>
        <p className="text-muted small mb-0">
          Her transfer kaydında rehber kendi kendine dolar. Buradan düzeltebilir, pasife alabilir, yeni kayıt
          ekleyebilir ve şoförlere araç atayabilirsiniz.
        </p>
      </div>

      <ul className="nav nav-tabs mb-3">
        <li className="nav-item">
          <button
            type="button"
            className={`nav-link ${tab === 'drivers' ? 'active' : ''}`}
            onClick={() => setTab('drivers')}
          >
            <i className="fas fa-id-card me-2" />
            Şoförler
          </button>
        </li>
        <li className="nav-item">
          <button
            type="button"
            className={`nav-link ${tab === 'vehicles' ? 'active' : ''}`}
            onClick={() => setTab('vehicles')}
          >
            <i className="fas fa-truck-front me-2" />
            Araçlar
          </button>
        </li>
      </ul>

      {tab === 'drivers' ? (
        <Drivers toast={toast} askCode={askCode} canManage={canManage} />
      ) : (
        <Vehicles toast={toast} askCode={askCode} canManage={canManage} />
      )}
    </div>
  );
}
