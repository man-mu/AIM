import { useEffect, useId, useRef, useState } from 'react';
import type { ReactNode } from 'react';

/**
 * 轻量下拉菜单（原生 Popover API）：
 * - popover="auto"：浏览器托管 light-dismiss（点外部/Esc 关闭）与顶层渲染；
 * - 打开后测量面板尺寸定位：下方放不下自动向上翻转，水平方向夹紧到视口内
 *   （账户菜单在屏幕底部、消息操作在列表底部等场景不会弹出视口外）；
 * - jsdom 不支持 popover 时降级为普通条件渲染。
 */
export interface MenuItem {
  key: string;
  label: string;
  danger?: boolean;
  disabled?: boolean;
  onSelect: () => void;
}

export interface MenuProps {
  items: MenuItem[];
  trigger: ReactNode;
  triggerLabel: string;
  align?: 'start' | 'end';
  triggerClassName?: string;
}

const VIEWPORT_GAP = 8;
const TRIGGER_GAP = 6;

export function Menu({ items, trigger, triggerLabel, align = 'end', triggerClassName = '' }: MenuProps): React.JSX.Element {
  const popoverId = useId();
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const [fallbackOpen, setFallbackOpen] = useState(false);
  const supportsPopover =
    typeof HTMLElement !== 'undefined' && Object.prototype.hasOwnProperty.call(HTMLElement.prototype, 'popover');

  const position = (): void => {
    const button = buttonRef.current;
    const panel = panelRef.current;
    if (!button || !panel) {
      return;
    }
    const rect = button.getBoundingClientRect();
    const panelHeight = panel.offsetHeight;
    const panelWidth = panel.offsetWidth;

    // 垂直：默认向下；下方放不下且上方放得下 → 向上翻转。
    const fitsBelow = rect.bottom + TRIGGER_GAP + panelHeight + VIEWPORT_GAP <= window.innerHeight;
    const fitsAbove = rect.top - TRIGGER_GAP - panelHeight - VIEWPORT_GAP >= 0;
    const top = !fitsBelow && fitsAbove ? rect.top - TRIGGER_GAP - panelHeight : rect.bottom + TRIGGER_GAP;

    // 水平：按 align 对齐后夹紧到视口内。
    const preferredLeft = align === 'end' ? rect.right - panelWidth : rect.left;
    const left = Math.min(Math.max(VIEWPORT_GAP, preferredLeft), window.innerWidth - panelWidth - VIEWPORT_GAP);

    panel.style.position = 'fixed';
    panel.style.margin = '0';
    panel.style.top = `${Math.round(Math.max(VIEWPORT_GAP, top))}px`;
    panel.style.left = `${Math.round(left)}px`;
    panel.style.right = 'auto';
    panel.style.bottom = 'auto';
  };

  // Popover 模式：打开（toggle → open）后测量并定位。
  useEffect(() => {
    const panel = panelRef.current;
    if (!supportsPopover || !panel) {
      return undefined;
    }
    const onToggle = (event: Event): void => {
      if ((event as { newState?: string }).newState === 'open') {
        position();
      }
    };
    panel.addEventListener('toggle', onToggle);
    return () => panel.removeEventListener('toggle', onToggle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [supportsPopover]);

  // 降级模式：渲染后定位 + 点外关闭。
  useEffect(() => {
    if (!supportsPopover && fallbackOpen) {
      position();
      const close = (event: MouseEvent): void => {
        if (!panelRef.current?.contains(event.target as Node) && event.target !== buttonRef.current) {
          setFallbackOpen(false);
        }
      };
      document.addEventListener('mousedown', close);
      return () => document.removeEventListener('mousedown', close);
    }
    return undefined;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fallbackOpen, supportsPopover]);

  const closePanel = (): void => {
    if (supportsPopover) {
      panelRef.current?.hidePopover?.();
    } else {
      setFallbackOpen(false);
    }
  };

  const panelBody = (
    <div role="menu" aria-label={triggerLabel} className="min-w-36 py-1">
      {items.map((item) => (
        <button
          key={item.key}
          type="button"
          role="menuitem"
          disabled={item.disabled}
          onClick={() => {
            closePanel();
            item.onSelect();
          }}
          className={`block w-full px-3.5 py-2 text-left text-[13px] transition disabled:cursor-not-allowed disabled:opacity-40 ${
            item.danger ? 'text-[#e5484d] hover:bg-[#fef1f1]' : 'text-[#1d1d1f] hover:bg-black/[0.05]'
          }`}
        >
          {item.label}
        </button>
      ))}
    </div>
  );

  const panelClass = 'aim-menu rounded-xl border border-black/[0.08] bg-white/95 p-0 shadow-[0_12px_40px_rgba(0,0,0,0.14)] backdrop-blur-xl';

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        aria-label={triggerLabel}
        aria-haspopup="menu"
        className={triggerClassName}
        {...(supportsPopover ? { popoverTarget: popoverId } : {})}
        onClick={() => {
          if (!supportsPopover) {
            setFallbackOpen((current) => !current);
          }
          // popover 模式的开合与定位由浏览器 + toggle 事件接管。
        }}
      >
        {trigger}
      </button>
      {supportsPopover ? (
        <div ref={panelRef} id={popoverId} popover="auto" className={panelClass}>
          {panelBody}
        </div>
      ) : fallbackOpen ? (
        <div ref={panelRef} className={`${panelClass} z-50`} style={{ position: 'fixed' }}>
          {panelBody}
        </div>
      ) : null}
    </>
  );
}
