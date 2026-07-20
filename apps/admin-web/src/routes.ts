export interface AppRoute {
  name: string;
  url: string;
  icon: string;
  /** When true, only ADMIN-role users see the nav entry (UX only). */
  adminOnly?: boolean;
}

/**
 * Navigation model. Ordering is the display order. `adminOnly` hides admin-oriented
 * entries from investors as a UX affordance — the services still enforce authorization.
 */
export const ROUTES: AppRoute[] = [
  { name: "Dashboard", url: "/dashboard", icon: "pi pi-home" },
  { name: "Orders", url: "/orders", icon: "pi pi-book" },
  { name: "Executions", url: "/executions", icon: "pi pi-database" },
  { name: "Accounts", url: "/accounts", icon: "pi pi-dollar" },
  {
    name: "Reference Data",
    url: "/reference",
    icon: "pi pi-sitemap",
    adminOnly: true,
  },
];

export function routesForRole(isAdmin: boolean): AppRoute[] {
  return ROUTES.filter((route) => !route.adminOnly || isAdmin);
}
