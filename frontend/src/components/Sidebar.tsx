'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { useAuth } from '@/lib/auth-context';
import {
  LayoutDashboard,
  Database,
  BarChart3,
  CloudUpload,
  Settings,
  ChevronRight,
  LogOut,
  ListTodo
} from 'lucide-react';

const navigation = [
  { name: 'Dashboard', href: '/', icon: LayoutDashboard },
  { name: 'Clusters', href: '/clusters', icon: Database },
  { name: 'Monitoring', href: '/monitoring', icon: BarChart3 },
  { name: 'Backups', href: '/backups', icon: CloudUpload },
  { name: 'Tasks', href: '/tasks', icon: ListTodo },
  { name: 'Settings', href: '/settings', icon: Settings },
];

export function Sidebar() {
  const pathname = usePathname();
  const { user, logout } = useAuth();

  const userInitials = user?.username?.slice(0, 2).toUpperCase() || 'U';

  return (
    <aside className="fixed left-0 top-0 h-screen w-64 border-r border-white/5 bg-zinc-950 flex flex-col">
      {/* Logo */}
      <div className="h-16 flex items-center px-6 border-b border-white/5">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-emerald-400 to-emerald-600 flex items-center justify-center shadow-lg shadow-emerald-500/20">
            <Database className="w-4 h-4 text-white" />
          </div>
          <div>
            <span className="font-semibold text-white tracking-tight">DBaaS</span>
            <span className="text-xs text-zinc-500 block -mt-0.5">Platform</span>
          </div>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-6 space-y-1">
        {navigation.map((item) => {
          const isActive = pathname === item.href ||
            (item.href !== '/' && pathname.startsWith(item.href));

          return (
            <Link
              key={item.name}
              href={item.href}
              className={cn(
                'group flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-150',
                isActive
                  ? 'bg-white/5 text-white'
                  : 'text-zinc-400 hover:text-white hover:bg-white/5'
              )}
            >
              <item.icon className={cn(
                'w-[18px] h-[18px] transition-colors',
                isActive ? 'text-emerald-400' : 'text-zinc-500 group-hover:text-zinc-300'
              )} />
              <span className="flex-1">{item.name}</span>
              {isActive && (
                <ChevronRight className="w-4 h-4 text-zinc-600" />
              )}
            </Link>
          );
        })}
      </nav>

      {/* User Profile */}
      <div className="p-4 border-t border-white/5">
        <div className="flex items-center gap-3 px-2 py-2 rounded-lg">
          <Avatar className="w-8 h-8">
            <AvatarImage src="" />
            <AvatarFallback className="bg-gradient-to-br from-violet-400 to-violet-600 text-white text-xs font-medium">
              {userInitials}
            </AvatarFallback>
          </Avatar>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-white truncate">
              {user?.username || 'User'}
            </p>
            <p className="text-xs text-zinc-500 truncate">
              {user?.email || user?.role || 'User'}
            </p>
          </div>
          <button
            onClick={logout}
            className="p-2 rounded-lg text-zinc-500 hover:text-red-400 hover:bg-red-500/10 transition-all"
            aria-label="Đăng xuất"
          >
            <LogOut className="w-4 h-4" aria-hidden="true" />
          </button>
        </div>
      </div>
    </aside>
  );
}

export default Sidebar;
