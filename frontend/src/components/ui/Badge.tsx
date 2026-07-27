/** 未读角标：0 隐藏，99+ 截断；muted 形态用于免打扰会话。 */
export interface UnreadBadgeProps {
  count: number;
  muted?: boolean;
  className?: string;
}

export function UnreadBadge({ count, muted = false, className = '' }: UnreadBadgeProps): React.JSX.Element | null {
  if (count <= 0) {
    return null;
  }
  const text = count > 99 ? '99+' : String(count);
  return (
    <span
      aria-label={`未读 ${count} 条`}
      className={`grid min-w-4 place-items-center rounded-full px-1 text-[10px] font-semibold leading-4 text-white ${
        muted ? 'bg-[#b8b8bd]' : 'bg-[#fa3e2c]'
      } ${className}`}
    >
      {text}
    </span>
  );
}

/** 小圆点（导航栏聚合提示）。 */
export function DotBadge({ show, className = '' }: { show: boolean; className?: string }): React.JSX.Element | null {
  if (!show) {
    return null;
  }
  return <span aria-hidden className={`size-2 rounded-full bg-[#fa3e2c] ${className}`} />;
}
