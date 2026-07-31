import { useMemo } from 'react';

/**
 * 头像：有图显图，无图用名字首字符 + 由 id 稳定散列出的柔和底色。
 * 同一用户在任何界面颜色一致（散列而非随机）。
 */
export interface AvatarProps {
  name: string;
  src?: string;
  /** 参与配色散列的稳定 id（默认用 name）。 */
  colorKey?: string;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  shape?: 'circle' | 'rounded';
  className?: string;
}

const SIZE_CLASS: Record<NonNullable<AvatarProps['size']>, string> = {
  sm: 'size-7 text-[11px]',
  md: 'size-9 text-sm',
  lg: 'size-12 text-lg',
  xl: 'size-16 text-2xl',
};

/** Apple 系柔和底色盘（前景统一深灰，保证对比度）。 */
const PALETTE = [
  '#dbe8f7',
  '#e2f0e5',
  '#fdeadb',
  '#efe4f7',
  '#fde3e7',
  '#e0f0f4',
  '#f0ead8',
  '#e6e6f0',
] as const;

const FOREGROUND = ['#24527a', '#2f5e3b', '#8a5320', '#5d3b7a', '#8a2f42', '#1f5f6e', '#6e5a1f', '#44447a'] as const;

function hashString(value: string): number {
  let hash = 5381;
  for (let i = 0; i < value.length; i += 1) {
    hash = (hash * 33) ^ value.charCodeAt(i);
  }
  return hash >>> 0;
}

export function Avatar({ name, src, colorKey, size = 'md', shape = 'circle', className = '' }: AvatarProps): React.JSX.Element {
  const paletteIndex = useMemo(() => hashString(colorKey || name) % PALETTE.length, [colorKey, name]);
  const radius = shape === 'circle' ? 'rounded-full' : 'rounded-xl';
  const initial = name.trim().slice(0, 1).toUpperCase() || '?';

  if (src) {
    return (
      <img
        src={src}
        alt=""
        aria-hidden
        loading="lazy"
        className={`${SIZE_CLASS[size]} ${radius} shrink-0 object-cover ${className}`}
      />
    );
  }

  return (
    <span
      aria-hidden
      className={`grid ${SIZE_CLASS[size]} ${radius} shrink-0 select-none place-items-center font-semibold ${className}`}
      style={{ backgroundColor: PALETTE[paletteIndex], color: FOREGROUND[paletteIndex] }}
    >
      {initial}
    </span>
  );
}
