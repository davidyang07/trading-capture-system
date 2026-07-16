export const ROUTES = [
  {
    name: "Dashboard",
    url: "/dashboard",
    icon: "pi pi-home",
    role: ["admin", "investor"],
  },
  {
    name: "Execution",
    url: "/execution",
    icon: "pi pi-database",
    role: ["admin"],
  },
  {
    name: "Financial Account Management",
    url: "/accounts",
    icon: "pi pi-dollar",
    role: ["admin"],
  },
  {
    name: "User Management",
    url: "/users",
    icon: "pi pi-user",
    role: ["admin"],
  },
];

export const getRoutes = () => {
  const role = process.env.APP_ROLE;
  const routes: string[] = [];
  if (role) {
    ROUTES.map((route: any) => {
      (route.role ?? []).includes(role) && routes.push(route);
    });
  }
  return routes;
};
