'use client';

import { useState } from 'react';
import { 
  Tabs, 
  TabsContent, 
  TabsList, 
  TabsTrigger 
} from '@/components/ui/tabs';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { 
  User, 
  Shield, 
  Key, 
  Bell, 
  Mail, 
  Plus, 
  Trash2,
  Check,
  Copy,
  Settings as SettingsIcon,
  LogOut
} from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';
import { Switch } from '@/components/ui/switch';

export default function SettingsPage() {
  return (
    <div className="space-y-8 animate-in fade-in duration-500 max-w-5xl mx-auto">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-white tracking-tight">Settings</h1>
        <p className="text-sm text-zinc-500 mt-1">Manage project preferences and access control</p>
      </div>

      <Tabs defaultValue="general" className="w-full">
        <TabsList className="bg-zinc-900/50 border border-white/5 p-1 h-auto w-full justify-start rounded-xl overflow-x-auto">
          <SettingsTab value="general" icon={<SettingsIcon className="w-4 h-4" />}>General</SettingsTab>
          <SettingsTab value="team" icon={<User className="w-4 h-4" />}>Team</SettingsTab>
          <SettingsTab value="api-keys" icon={<Key className="w-4 h-4" />}>API Keys</SettingsTab>
          <SettingsTab value="notifications" icon={<Bell className="w-4 h-4" />}>Notifications</SettingsTab>
        </TabsList>

        <div className="mt-6 space-y-8">
          <TabsContent value="general" className="space-y-6">
            <GeneralSection />
          </TabsContent>
          <TabsContent value="team" className="space-y-6">
            <TeamSection />
          </TabsContent>
          <TabsContent value="api-keys" className="space-y-6">
            <ApiKeysSection />
          </TabsContent>
          <TabsContent value="notifications" className="space-y-6">
            <NotificationsSection />
          </TabsContent>
        </div>
      </Tabs>
    </div>
  );
}

function SettingsTab({ value, icon, children }: { value: string; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <TabsTrigger 
      value={value}
      className="data-[state=active]:bg-zinc-800 data-[state=active]:text-white data-[state=active]:shadow text-zinc-400 hover:text-white flex items-center gap-2 px-4 py-2"
    >
      {icon}
      {children}
    </TabsTrigger>
  );
}

function GeneralSection() {
  return (
    <div className="glass-card rounded-xl divide-y divide-white/5">
      <div className="p-6">
        <h3 className="text-lg font-semibold text-white mb-1">Project Information</h3>
        <p className="text-sm text-zinc-500 mb-6">Manage your project identity and owner</p>
        
        <div className="space-y-4 max-w-xl">
          <div className="space-y-2">
            <label htmlFor="projectName" className="text-sm font-medium text-zinc-300">Project Name</label>
            <Input id="projectName" defaultValue="DBaaS Platform" className="bg-zinc-900 border-white/10" />
          </div>
          <div className="space-y-2">
            <label htmlFor="projectId" className="text-sm font-medium text-zinc-300">Project ID</label>
            <div className="flex items-center gap-2">
               <code id="projectId" className="bg-zinc-950 px-3 py-2 rounded-md font-mono text-sm text-zinc-400 border border-white/5 flex-1 block">
                 proj_84979db53a9e
               </code>
               <Button variant="outline" size="icon" aria-label="Copy Project ID" className="border-white/10 hover:bg-white/5 hover:text-white">
                 <Copy className="w-4 h-4" aria-hidden="true" />
               </Button>
            </div>
          </div>
        </div>
      </div>
      <div className="p-6 flex justify-end gap-3 bg-zinc-900/30">
        <Button variant="ghost" className="text-zinc-400 hover:text-white">Discard</Button>
        <Button className="bg-emerald-600 hover:bg-emerald-500 text-white">Save Changes</Button>
      </div>
    </div>
  );
}

function TeamSection() {
  const members = [
    { name: 'Anh Vu', email: 'anhvu@example.com', role: 'Owner', avatar: 'AV' },
    { name: 'Dev Team', email: 'dev@example.com', role: 'Admin', avatar: 'DT' },
    { name: 'Viewer', email: 'view@example.com', role: 'Member', avatar: 'VI' },
  ];

  return (
    <div className="glass-card rounded-xl p-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h3 className="text-lg font-semibold text-white">Team Members</h3>
          <p className="text-sm text-zinc-500">Manage who has access to this project</p>
        </div>
        <Button className="bg-white/10 hover:bg-white/20 text-white border border-white/5 gap-2">
          <Plus className="w-4 h-4" />
          Invite Member
        </Button>
      </div>

      <div className="space-y-4">
        {members.map((member, i) => (
          <div key={i} className="flex items-center justify-between p-4 rounded-lg bg-zinc-900/50 border border-white/5">
            <div className="flex items-center gap-4">
              <Avatar className="h-10 w-10 border border-white/10">
                <AvatarFallback className="bg-zinc-800 text-zinc-300">{member.avatar}</AvatarFallback>
              </Avatar>
              <div>
                <p className="text-sm font-medium text-white">{member.name}</p>
                <div className="flex items-center gap-2">
                   <Mail className="w-3 h-3 text-zinc-500" />
                   <p className="text-xs text-zinc-500">{member.email}</p>
                </div>
              </div>
            </div>
            <div className="flex items-center gap-4">
              <Badge variant="outline" className="bg-zinc-950/50 border-white/10 text-zinc-400">
                {member.role}
              </Badge>
              <Button variant="ghost" size="icon" aria-label={`Remove ${member.name}`} className="text-zinc-500 hover:text-red-400 hover:bg-red-500/10 h-8 w-8">
                <Trash2 className="w-4 h-4" aria-hidden="true" />
              </Button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function ApiKeysSection() {
  return (
    <div className="glass-card rounded-xl p-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h3 className="text-lg font-semibold text-white">API Keys</h3>
          <p className="text-sm text-zinc-500">Manage API keys for external access</p>
        </div>
        <Button className="bg-emerald-600 hover:bg-emerald-500 text-white gap-2">
          <Plus className="w-4 h-4" />
          Create New Key
        </Button>
      </div>

      <div className="space-y-3">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between p-4 rounded-lg bg-zinc-900/50 border border-emerald-500/20 shadow-[0_0_15px_rgba(16,185,129,0.05)]">
           <div className="flex items-center gap-4 mb-3 sm:mb-0">
             <div className="h-10 w-10 rounded-full bg-emerald-500/10 flex items-center justify-center border border-emerald-500/20">
               <Key className="w-5 h-5 text-emerald-500" />
             </div>
             <div>
               <p className="text-sm font-medium text-white">Production API Key</p>
               <p className="text-xs text-zinc-500">Last used: 2 mins ago</p>
             </div>
           </div>
           <div className="flex items-center gap-2">
             <code className="bg-black px-3 py-1.5 rounded text-xs font-mono text-zinc-400 block w-32 truncate">
               sk_live_8f9...2x9
             </code>
             <Button variant="ghost" size="icon" aria-label="Copy API key" className="h-8 w-8 text-zinc-400 hover:text-white">
               <Copy className="w-3.5 h-3.5" aria-hidden="true" />
             </Button>
             <Button variant="ghost" size="icon" aria-label="Delete Production API key" className="h-8 w-8 text-zinc-400 hover:text-red-400">
               <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
             </Button>
           </div>
        </div>

        <div className="flex flex-col sm:flex-row sm:items-center justify-between p-4 rounded-lg bg-zinc-900/50 border border-white/5 opacity-60">
           <div className="flex items-center gap-4 mb-3 sm:mb-0">
             <div className="h-10 w-10 rounded-full bg-zinc-800 flex items-center justify-center border border-zinc-700">
               <Key className="w-5 h-5 text-zinc-500" />
             </div>
             <div>
               <p className="text-sm font-medium text-zinc-300">Staging Key</p>
               <p className="text-xs text-zinc-500">Created: 5 days ago</p>
             </div>
           </div>
           <div className="flex items-center gap-2">
             <code className="bg-black px-3 py-1.5 rounded text-xs font-mono text-zinc-600 block w-32 truncate">
               sk_test_7a1...9b2
             </code>
             <Button variant="ghost" size="icon" aria-label="Delete Staging API key" className="h-8 w-8 text-zinc-400 hover:text-red-400">
               <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
             </Button>
           </div>
        </div>
      </div>
    </div>
  );
}

function NotificationsSection() {
  return (
    <div className="glass-card rounded-xl p-6 space-y-6">
       <div>
        <h3 className="text-lg font-semibold text-white">Notification Preferences</h3>
        <p className="text-sm text-zinc-500">Choose how you want to be notified</p>
      </div>

      <div className="space-y-4">
        <div className="flex items-center justify-between p-4 rounded-lg bg-zinc-900/50 border border-white/5">
          <div className="flex items-center gap-3">
            <Mail className="w-5 h-5 text-zinc-400" />
            <div>
              <p className="text-sm font-medium text-white">Email Alerts</p>
              <p className="text-xs text-zinc-500">Receive crucial alerts via email</p>
            </div>
          </div>
          <Switch defaultChecked className="data-[state=checked]:bg-emerald-500" />
        </div>

        <div className="flex items-center justify-between p-4 rounded-lg bg-zinc-900/50 border border-white/5">
          <div className="flex items-center gap-3">
            <Bell className="w-5 h-5 text-zinc-400" />
            <div>
              <p className="text-sm font-medium text-white">In-App Notifications</p>
              <p className="text-xs text-zinc-500">Show pop-up toasts within the dashboard</p>
            </div>
          </div>
          <Switch defaultChecked className="data-[state=checked]:bg-emerald-500" />
        </div>
      </div>
    </div>
  );
}
