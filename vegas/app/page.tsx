"use client";

import { useState, useEffect, useMemo } from "react";
import { supabase } from "../lib/supabase";
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell } from "recharts";

interface Sale {
  id: number;
  created_at: string;
  total: string;
  payment_method: string;
}

interface SaleItem {
  id: number;
  product_name: string;
}

interface Product {
  id: number;
  name: string;
  stock_quantity: string;
  min_stock_level: string;
  category: string;
}

type TabType = "overview" | "inventory" | "history" | "analytics";

export default function Dashboard() {
  const [activeTab, setActiveTab] = useState<TabType>("overview");
  const [sales, setSales] = useState<Sale[]>([]);
  const [saleItems, setSaleItems] = useState<SaleItem[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [inventorySearchQuery, setInventorySearchQuery] = useState("");

  const fetchData = async () => {
    try {
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      const todayTimestamp = today.getTime();

      const { data: salesData, error: salesError } = await supabase
        .from("cloud_sales")
        .select("*")
        .order("created_at", { ascending: false });

      if (salesError) throw salesError;

      const { data: saleItemsData, error: saleItemsError } = await supabase
        .from("cloud_sale_items")
        .select("id, product_name");

      if (saleItemsError) throw saleItemsError;

      const { data: productsData, error: productsError } = await supabase
        .from("products")
        .select("id, name, stock_quantity, min_stock_level, category");

      if (productsError) throw productsError;

      setSales(salesData || []);
      setSaleItems(saleItemsData || []);
      setProducts(productsData || []);
    } catch (error) {
      console.error("Error fetching data:", error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleRefresh = () => {
    setRefreshing(true);
    fetchData();
  };

  const todayRevenue = useMemo(() => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayTimestamp = today.getTime();
    
    return sales.reduce((sum, sale) => {
      const saleDate = new Date(sale.created_at);
      saleDate.setHours(0, 0, 0, 0);
      if (saleDate.getTime() === todayTimestamp) {
        return sum + Number(sale.total || "0");
      }
      return sum;
    }, 0);
  }, [sales]);

  const lifetimeRevenue = useMemo(() => {
    return sales.reduce((sum, sale) => sum + Number(sale.total || "0"), 0);
  }, [sales]);

  const totalSalesCount = sales.length;

  const lowStockProducts = useMemo(() => {
    return products.filter(p => Number(p.stock_quantity || "0") <= Number(p.min_stock_level || "5"));
  }, [products]);

  const fullInventory = useMemo(() => {
    return products.filter(p => Number(p.stock_quantity || "0") > Number(p.min_stock_level || "5"));
  }, [products]);

  const weeklyRevenue = useMemo(() => {
    const now = new Date();
    const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    return sales.reduce((sum, sale) => {
      const saleDate = new Date(sale.created_at);
      if (saleDate >= weekAgo) {
        return sum + Number(sale.total || "0");
      }
      return sum;
    }, 0);
  }, [sales]);

  const monthlyRevenue = useMemo(() => {
    const now = new Date();
    const monthAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
    return sales.reduce((sum, sale) => {
      const saleDate = new Date(sale.created_at);
      if (saleDate >= monthAgo) {
        return sum + Number(sale.total || "0");
      }
      return sum;
    }, 0);
  }, [sales]);

  const bestSellers = useMemo(() => {
    const productCount: Record<string, number> = {};
    saleItems.forEach(item => {
      if (item.product_name) {
        productCount[item.product_name] = (productCount[item.product_name] || 0) + 1;
      }
    });
    return Object.entries(productCount)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 3)
      .map(([name, count]) => ({ name, count }));
  }, [saleItems]);

  const paymentSplit = useMemo(() => {
    const total = sales.length;
    if (total === 0) return { cash: 0, mpesa: 0 };
    const cashCount = sales.filter(s => s.payment_method?.toLowerCase() === "cash").length;
    const mpesaCount = sales.filter(s => s.payment_method?.toLowerCase().includes("mpesa") || s.payment_method?.toLowerCase() === "mobile_money").length;
    return {
      cash: Math.round((cashCount / total) * 100),
      mpesa: Math.round((mpesaCount / total) * 100)
    };
  }, [sales]);

  const peakHour = useMemo(() => {
    const hourCount: Record<number, number> = {};
    sales.forEach(sale => {
      const hour = new Date(sale.created_at).getHours();
      hourCount[hour] = (hourCount[hour] || 0) + 1;
    });
    const entries = Object.entries(hourCount);
    if (entries.length === 0) return "N/A";
    const peak = entries.sort((a, b) => Number(b[1]) - Number(a[1]))[0];
    return `${peak[0]}:00`;
  }, [sales]);

  const weeklyRevenueByDay = useMemo(() => {
    const days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
    const dayTotals: Record<string, number> = {};
    days.forEach(day => dayTotals[day] = 0);
    
    const now = new Date();
    const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    
    sales.forEach(sale => {
      const saleDate = new Date(sale.created_at);
      if (saleDate >= weekAgo) {
        const dayName = days[saleDate.getDay()];
        dayTotals[dayName] += parseFloat(String(sale.total) || "0");
      }
    });
    
    return days.map(day => ({
      name: day,
      revenue: dayTotals[day]
    }));
  }, [sales]);

  const averageSaleValue = useMemo(() => {
    if (sales.length === 0) return 0;
    const totalRevenue = sales.reduce((sum, sale) => sum + parseFloat(String(sale.total) || "0"), 0);
    return totalRevenue / sales.length;
  }, [sales]);

  const filteredInventory = useMemo(() => {
    if (!inventorySearchQuery.trim()) return fullInventory;
    const query = inventorySearchQuery.toLowerCase();
    return fullInventory.filter(p => 
      p.name?.toLowerCase().includes(query) ||
      p.category?.toLowerCase().includes(query)
    );
  }, [fullInventory, inventorySearchQuery]);

  const filteredSales = useMemo(() => {
    if (!searchQuery.trim()) return sales;
    const query = searchQuery.toLowerCase();
    return sales.filter(sale => 
      sale.id.toString().includes(query) ||
      Number(sale.total).toString().includes(query)
    );
  }, [sales, searchQuery]);

  const last5Sales = useMemo(() => sales.slice(0, 5), [sales]);

  const sparklineData = useMemo(() => {
    const last7Days: number[] = [];
    const now = new Date();
    
    for (let i = 6; i >= 0; i--) {
      const date = new Date(now);
      date.setDate(date.getDate() - i);
      date.setHours(0, 0, 0, 0);
      const dayStart = date.getTime();
      const dayEnd = new Date(date);
      dayEnd.setHours(23, 59, 59, 999);
      
      const dayRevenue = sales.reduce((sum, sale) => {
        const saleDate = new Date(sale.created_at);
        if (saleDate.getTime() >= dayStart && saleDate.getTime() <= dayEnd.getTime()) {
          return sum + Number(sale.total || "0");
        }
        return sum;
      }, 0);
      
      last7Days.push(dayRevenue);
    }
    
    const max = Math.max(...last7Days, 1);
    return last7Days.map(v => (v / max) * 100);
  }, [sales]);

  const formatTime = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleTimeString("en-KE", {
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  const formatDateTime = (dateString: string) => {
    const date = new Date(dateString);
    return new Intl.DateTimeFormat("en-KE", {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    }).format(date);
  };

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat("en-KE", {
      style: "currency",
      currency: "KES",
      minimumFractionDigits: 0,
    }).format(amount);
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-graphite-950 flex items-center justify-center">
        <div className="text-emerald-500 text-xl">Loading...</div>
      </div>
    );
  }

  const renderSparkline = () => {
    const points = sparklineData.map((v, i) => {
      const x = (i / (sparklineData.length - 1)) * 100;
      const y = 100 - v;
      return `${x},${y}`;
    }).join(" ");
    
    return (
      <svg viewBox="0 0 100 40" className="w-full h-12" preserveAspectRatio="none">
        <polyline
          fill="none"
          stroke="#10B981"
          strokeWidth="2"
          points={points}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <circle cx={points.split(" ").pop()?.split(",")[0]} cy={points.split(" ").pop()?.split(",")[1]} r="2" fill="#10B981" />
      </svg>
    );
  };

  const renderOverview = () => (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <div className="bg-graphite-800 p-5 rounded-2xl border border-graphite-700">
          <div className="text-graphite-400 text-xs font-medium uppercase tracking-wider mb-1">Today</div>
          <div className="text-2xl font-bold text-emerald-500">
            {formatCurrency(todayRevenue)}
          </div>
        </div>
        <div className="bg-graphite-800 p-5 rounded-2xl border border-graphite-700">
          <div className="text-graphite-400 text-xs font-medium uppercase tracking-wider mb-1">Lifetime</div>
          <div className="text-2xl font-bold text-white">
            {formatCurrency(lifetimeRevenue)}
          </div>
        </div>
        <div className="bg-graphite-800 p-5 rounded-2xl border border-graphite-700">
          <div className="text-graphite-400 text-xs font-medium uppercase tracking-wider mb-1">Transactions</div>
          <div className="text-2xl font-bold text-white">{totalSalesCount}</div>
        </div>
        <div className="bg-graphite-800 p-5 rounded-2xl border border-graphite-700">
          <div className="text-graphite-400 text-xs font-medium uppercase tracking-wider mb-1">Low Stock</div>
          <div className="text-2xl font-bold text-red-400">{lowStockProducts.length}</div>
        </div>
      </div>

      <div className="bg-graphite-800 p-5 rounded-2xl border border-graphite-700">
        <div className="flex justify-between items-center mb-4">
          <div className="text-white font-semibold">Daily Sales Trend</div>
          <div className="text-emerald-500 text-xs">Last 7 days</div>
        </div>
        {renderSparkline()}
      </div>

      <div className="bg-graphite-800 rounded-2xl border border-graphite-700 overflow-hidden">
        <div className="p-5 border-b border-graphite-700">
          <h2 className="text-lg font-semibold text-white">Current Transactions</h2>
        </div>
        {last5Sales.length === 0 ? (
          <div className="p-8 text-center text-graphite-400">No transactions yet</div>
        ) : (
          <div className="divide-y divide-graphite-700">
            {last5Sales.map((sale) => (
              <div key={sale.id} className="p-4 flex justify-between items-center">
                <div>
                  <div className="text-white font-medium">#{sale.id}</div>
                  <div className="text-graphite-400 text-sm">{formatTime(sale.created_at)}</div>
                </div>
                <div className="text-right">
                  <div className="text-emerald-500 font-semibold">{formatCurrency(Number(sale.total) || 0)}</div>
                  <div className={`text-xs ${sale.payment_method?.toLowerCase() === "cash" ? "text-emerald-400" : "text-blue-400"}`}>
                    {sale.payment_method || "N/A"}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );

  const renderInventory = () => (
    <div className="space-y-6">
      <div className="bg-red-500/10 border border-red-500/30 rounded-2xl p-5">
        <div className="flex items-center gap-2 mb-3">
          <div className="w-2 h-2 rounded-full bg-red-500"></div>
          <h2 className="text-lg font-semibold text-white">Low / Out of Stock</h2>
        </div>
        {lowStockProducts.length === 0 ? (
          <div className="text-graphite-400">All items are well stocked</div>
        ) : (
          <div className="space-y-2">
            {lowStockProducts.map((product) => (
              <div key={product.id} className="flex justify-between items-center py-2 border-b border-graphite-700 last:border-0">
                <div>
                  <span className="text-white">{product.name}</span>
                  {product.category && <span className="text-graphite-500 text-xs ml-2">({product.category})</span>}
                </div>
                <span className="bg-red-500/20 text-red-400 px-3 py-1 rounded-full text-sm font-medium">
                  {product.stock_quantity} units
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="relative">
        <input
          type="text"
          placeholder="Search products..."
          value={inventorySearchQuery}
          onChange={(e) => setInventorySearchQuery(e.target.value)}
          className="w-full bg-graphite-800 border border-graphite-700 rounded-xl px-4 py-3 text-white placeholder-graphite-500 focus:outline-none focus:border-emerald-500"
        />
        <div className="absolute right-4 top-1/2 -translate-y-1/2 text-graphite-500">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8"></circle>
            <path d="m21 21-4.3-4.3"></path>
          </svg>
        </div>
      </div>

      <div className="bg-graphite-800 rounded-2xl border border-graphite-700 overflow-hidden">
        <div className="p-5 border-b border-graphite-700">
          <h2 className="text-lg font-semibold text-white">Full Inventory</h2>
        </div>
        {filteredInventory.length === 0 ? (
          <div className="p-8 text-center text-graphite-400">
            {inventorySearchQuery ? "No matching products found" : "No items in inventory"}
          </div>
        ) : (
          <div className="divide-y divide-graphite-700">
            {filteredInventory.map((product) => (
              <div key={product.id} className="flex justify-between items-center p-4">
                <div>
                  <span className="text-white">{product.name}</span>
                  {product.category && <span className="text-graphite-500 text-xs ml-2">({product.category})</span>}
                </div>
                <span className="bg-emerald-500/20 text-emerald-500 px-3 py-1 rounded-full text-sm font-medium">
                  {product.stock_quantity} units
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );

  const renderHistory = () => (
    <div className="space-y-4">
      <div className="relative">
        <input
          type="text"
          placeholder="Search by ID or Amount..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full bg-graphite-800 border border-graphite-700 rounded-xl px-4 py-3 text-white placeholder-graphite-500 focus:outline-none focus:border-emerald-500"
        />
        <div className="absolute right-4 top-1/2 -translate-y-1/2 text-graphite-500">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8"></circle>
            <path d="m21 21-4.3-4.3"></path>
          </svg>
        </div>
      </div>

      <div className="bg-graphite-800 rounded-2xl border border-graphite-700 overflow-hidden">
        <div className="divide-y divide-graphite-700">
          {filteredSales.length === 0 ? (
            <div className="p-8 text-center text-graphite-400">
              {searchQuery ? "No matching sales found" : "No sales history"}
            </div>
          ) : (
            filteredSales.map((sale) => (
              <div key={sale.id} className="p-4 flex justify-between items-center">
                <div>
                  <div className="text-white font-medium">Sale #{sale.id}</div>
                  <div className="text-graphite-400 text-sm">{formatDateTime(sale.created_at)}</div>
                </div>
                <div className="text-right">
                  <div className="text-emerald-500 font-semibold text-lg">{formatCurrency(Number(sale.total) || 0)}</div>
                  <div className={`text-xs ${sale.payment_method?.toLowerCase() === "cash" ? "text-emerald-400" : "text-blue-400"}`}>
                    {sale.payment_method || "N/A"}
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );

  const renderAnalytics = () => (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <div className="bg-graphite-800 p-5 rounded-2xl border border-graphite-700">
          <div className="text-graphite-400 text-xs font-medium uppercase tracking-wider mb-1">Weekly Revenue</div>
          <div className="text-2xl font-bold text-emerald-500">
            {formatCurrency(weeklyRevenue)}
          </div>
        </div>
        <div className="bg-graphite-800 p-5 rounded-2xl border border-graphite-700">
          <div className="text-graphite-400 text-xs font-medium uppercase tracking-wider mb-1">Monthly Revenue</div>
          <div className="text-2xl font-bold text-white">
            {formatCurrency(monthlyRevenue)}
          </div>
        </div>
      </div>

      <div className="bg-graphite-800 rounded-2xl border border-graphite-700 p-5">
        <h2 className="text-lg font-semibold text-white mb-4">Weekly Revenue by Day</h2>
        <div className="h-48">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={weeklyRevenueByDay}>
              <XAxis 
                dataKey="name" 
                stroke="#64748b" 
                fontSize={12}
                tickLine={false}
                axisLine={false}
              />
              <YAxis 
                stroke="#64748b" 
                fontSize={12}
                tickLine={false}
                axisLine={false}
                tickFormatter={(value) => `Ksh${value}`}
              />
              <Tooltip 
                contentStyle={{ 
                  backgroundColor: '#1F1F1F', 
                  border: '1px solid #2A2A2A',
                  borderRadius: '8px',
                  color: '#fff'
                }}
                formatter={(value: number) => [formatCurrency(value), "Revenue"]}
                labelStyle={{ color: '#94a3b8' }}
              />
              <Bar dataKey="revenue" fill="#10B981" radius={[4, 4, 0, 0]}>
                {weeklyRevenueByDay.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill="#10B981" />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="bg-graphite-800 p-5 rounded-2xl border border-graphite-700">
          <div className="text-graphite-400 text-xs font-medium uppercase tracking-wider mb-1">Avg Sale</div>
          <div className="text-xl font-bold text-white">
            {formatCurrency(averageSaleValue)}
          </div>
        </div>
        <div className="bg-graphite-800 p-5 rounded-2xl border border-graphite-700">
          <div className="text-graphite-400 text-xs font-medium uppercase tracking-wider mb-1">Cash / M-Pesa</div>
          <div className="text-xl font-bold text-white">
            {paymentSplit.cash}% / {paymentSplit.mpesa}%
          </div>
        </div>
      </div>

      <div className="bg-graphite-800 rounded-2xl border border-graphite-700 p-5">
        <h2 className="text-lg font-semibold text-white mb-4">Best Sellers</h2>
        {bestSellers.length === 0 ? (
          <div className="text-graphite-400">No sales data yet</div>
        ) : (
          <div className="space-y-3">
            {bestSellers.map((item, index) => (
              <div key={item.name} className="flex justify-between items-center">
                <div className="flex items-center gap-3">
                  <span className="text-emerald-500 font-bold">{index + 1}.</span>
                  <span className="text-white">{item.name}</span>
                </div>
                <span className="bg-emerald-500/20 text-emerald-500 px-3 py-1 rounded-full text-sm">
                  {item.count} sold
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="bg-graphite-800 rounded-2xl border border-graphite-700 p-5">
        <h2 className="text-lg font-semibold text-white mb-4">Payment Methods</h2>
        <div className="space-y-2">
          <div className="flex justify-between items-center">
            <span className="text-white">Cash</span>
            <span className="text-emerald-500 font-semibold">{paymentSplit.cash}%</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-white">M-Pesa</span>
            <span className="text-blue-500 font-semibold">{paymentSplit.mpesa}%</span>
          </div>
        </div>
      </div>

      <div className="bg-graphite-800 rounded-2xl border border-graphite-700 p-5">
        <h2 className="text-lg font-semibold text-white mb-2">Peak Hour</h2>
        <div className="text-graphite-400 text-sm">
          Most sales occur at <span className="text-emerald-500 font-bold">{peakHour}</span>
        </div>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-graphite-950 pb-20">
      <div className="max-w-md mx-auto p-5">
        <div className="flex justify-between items-center mb-6">
          <h1 className="text-2xl font-bold text-white">Vegas POS</h1>
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            className="bg-emerald-500 text-graphite-950 px-4 py-2 rounded-lg font-semibold hover:bg-emerald-400 transition disabled:opacity-50"
          >
            {refreshing ? "..." : "Refresh"}
          </button>
        </div>

        {activeTab === "overview" && renderOverview()}
        {activeTab === "inventory" && renderInventory()}
        {activeTab === "history" && renderHistory()}
        {activeTab === "analytics" && renderAnalytics()}
      </div>

      <div className="fixed bottom-0 left-0 right-0 bg-graphite-900 border-t border-graphite-700">
        <div className="max-w-md mx-auto flex justify-around py-4">
          <button
            onClick={() => setActiveTab("overview")}
            className={`flex flex-col items-center gap-1 ${activeTab === "overview" ? "text-emerald-500" : "text-graphite-500"}`}
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <rect width="7" height="9" x="3" y="3" rx="1" />
              <rect width="7" height="5" x="14" y="3" rx="1" />
              <rect width="7" height="9" x="14" y="12" rx="1" />
              <rect width="7" height="5" x="3" y="16" rx="1" />
            </svg>
            <span className="text-xs font-medium">Overview</span>
          </button>
          <button
            onClick={() => setActiveTab("inventory")}
            className={`flex flex-col items-center gap-1 ${activeTab === "inventory" ? "text-emerald-500" : "text-graphite-500"}`}
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="m7.5 4.27 9 5.15" />
              <path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z" />
              <path d="m3.3 7 8.7 5 8.7-5" />
              <path d="M12 22V12" />
            </svg>
            <span className="text-xs font-medium">Inventory</span>
          </button>
          <button
            onClick={() => setActiveTab("history")}
            className={`flex flex-col items-center gap-1 ${activeTab === "history" ? "text-emerald-500" : "text-graphite-500"}`}
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 12" />
              <path d="M3 3v9h9" />
            </svg>
            <span className="text-xs font-medium">History</span>
          </button>
          <button
            onClick={() => setActiveTab("analytics")}
            className={`flex flex-col items-center gap-1 ${activeTab === "analytics" ? "text-emerald-500" : "text-graphite-500"}`}
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" x2="18" y1="20" y2="10" />
              <line x1="12" x2="12" y1="20" y2="4" />
              <line x1="6" x2="6" y1="20" y2="14" />
            </svg>
            <span className="text-xs font-medium">Analytics</span>
          </button>
        </div>
      </div>
    </div>
  );
}
