import type { ReactNode } from 'react';

export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
}): React.JSX.Element {
  return (
    <div className="grid h-full place-items-center p-8">
      <div className="grid max-w-60 justify-items-center gap-2 text-center">
        {icon ? <div className="mb-1 text-3xl text-[#c7c7cc]">{icon}</div> : null}
        <p className="text-sm font-medium text-[#1d1d1f]">{title}</p>
        {description ? <p className="text-xs leading-5 text-[#86868b]">{description}</p> : null}
        {action ? <div className="mt-2">{action}</div> : null}
      </div>
    </div>
  );
}
