'use client';

import { useState, useEffect } from 'react';
import { AreaChart, Area, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

const generateChartData = () => {
  return Array.from({ length: 20 }, (_, i) => ({
    time: `${i}s`,
    qps: Math.floor(1000 + Math.random() * 500),
  }));
};

export function RealtimeChart() {
  const [chartData, setChartData] = useState(generateChartData());

  useEffect(() => {
    const interval = setInterval(() => {
      setChartData(generateChartData());
    }, 3000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="glass-card rounded-xl p-5">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-medium text-white">Real-time Metrics</h3>
        <span className="flex items-center gap-1.5 text-xs text-emerald-400">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
          Live
        </span>
      </div>
      <div className="h-[160px]">
        <ResponsiveContainer width="100%" height="100%" minWidth={0} minHeight={160}>
          <AreaChart data={chartData}>
            <defs>
              <linearGradient id="colorQpsDetail" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#10b981" stopOpacity={0.15}/>
                <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
              </linearGradient>
            </defs>
            <XAxis dataKey="time" stroke="#52525b" fontSize={10} tickLine={false} axisLine={false} />
            <YAxis stroke="#52525b" fontSize={10} tickLine={false} axisLine={false} width={35} />
            <Tooltip 
              contentStyle={{ 
                background: 'hsl(240 6% 10%)', 
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: '8px',
                fontSize: '11px'
              }}
            />
            <Area type="monotone" dataKey="qps" stroke="#10b981" fill="url(#colorQpsDetail)" strokeWidth={1.5} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
