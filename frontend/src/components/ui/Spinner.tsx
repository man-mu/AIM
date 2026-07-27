/** 极简 spinner（CSS 动画，尊重 prefers-reduced-motion，见 index.css）。 */
export function Spinner({ size = 16, className = '' }: { size?: number; className?: string }): React.JSX.Element {
  return (
    <span
      role="status"
      aria-label="加载中"
      className={`aim-spinner inline-block shrink-0 rounded-full border-2 border-black/[0.12] border-t-[#0071e3] ${className}`}
      style={{ width: size, height: size }}
    />
  );
}
