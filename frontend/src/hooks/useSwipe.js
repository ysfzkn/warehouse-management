import { useCallback, useRef } from 'react';

/**
 * Birleşik kaydırma/sürükleme jesti — dokunmatik (touch), fare (mouse) ve kalem
 * (pen) girişini tek Pointer Events yoluyla ele alır. Yatay sürükleme eşiği
 * aşılınca onSwipeLeft/onSwipeRight tetiklenir; dikey hareket sayfayı normal
 * kaydırmaya bırakılır (touchAction: 'pan-y').
 *
 * Kullanım:
 *   const swipe = useSwipe({ onSwipeLeft: next, onSwipeRight: prev });
 *   <div {...swipe.handlers} style={{ ...swipe.style }} />
 *   // Sürükleme sonrası kazara tıklamayı engellemek için:
 *   <a onClickCapture={swipe.guardClick} />
 */
export default function useSwipe({ onSwipeLeft, onSwipeRight, threshold = 45 } = {}) {
  const state = useRef({ active: false, startX: 0, startY: 0, dx: 0, dy: 0, isDrag: false });

  const onPointerDown = useCallback((e) => {
    // Fare için yalnızca sol tuş; sağ/orta tuş sürüklemeyi başlatmasın.
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    state.current = { active: true, startX: e.clientX, startY: e.clientY, dx: 0, dy: 0, isDrag: false };
  }, []);

  const onPointerMove = useCallback((e) => {
    const s = state.current;
    if (!s.active) return;
    s.dx = e.clientX - s.startX;
    s.dy = e.clientY - s.startY;
    if (!s.isDrag && Math.abs(s.dx) > 8 && Math.abs(s.dx) > Math.abs(s.dy)) {
      s.isDrag = true;
    }
  }, []);

  const finish = useCallback(() => {
    const s = state.current;
    if (!s.active) return;
    s.active = false;
    if (Math.abs(s.dx) > Math.abs(s.dy) && Math.abs(s.dx) > threshold) {
      if (s.dx > 0) onSwipeRight && onSwipeRight();
      else onSwipeLeft && onSwipeLeft();
    }
  }, [onSwipeLeft, onSwipeRight, threshold]);

  // Sürükleme bir bağlantı/buton üzerinde bittiyse kazara tıklamayı yut.
  const guardClick = useCallback((e) => {
    if (state.current.isDrag) {
      e.preventDefault();
      e.stopPropagation();
    }
  }, []);

  return {
    isDragging: () => state.current.isDrag,
    guardClick,
    handlers: {
      onPointerDown,
      onPointerMove,
      onPointerUp: finish,
      onPointerLeave: finish,
      onPointerCancel: finish,
    },
    style: {
      touchAction: 'pan-y',
      userSelect: 'none',
      WebkitUserSelect: 'none',
    },
  };
}
