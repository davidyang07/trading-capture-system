import Image from "next/image";

/**
 * Public landing page (DESIGN.md › Landing). Presentational only: the route decides who sees it
 * and supplies the sign-in server action, so every "Sign in" button starts the Keycloak flow in
 * one click. Copy describes what the platform actually does — no invented figures or customers.
 */

type SignInAction = () => Promise<void>;

const FACTS = [
  { value: "4 services", label: "Trading, accounts, reference data and allocation" },
  { value: "FIX 4.4", label: "AllocationInstruction (35=J) intake over Kafka" },
  { value: "Avro events", label: "Versioned contracts behind a schema registry" },
  { value: "OAuth 2.0 + PKCE", label: "Keycloak sign-in; tokens never reach the browser" },
];

const PRODUCTS = [
  {
    icon: "pi-list",
    title: "Orders & executions",
    body: "Capture orders and their fills. Corrections and cancellations append to an amendment chain instead of rewriting what happened.",
    points: ["Correct and cancel with the full chain kept", "Fills locked against a racing allocation"],
  },
  {
    icon: "pi-wallet",
    title: "Accounts & ledger",
    body: "Cash and securities ledgers where every posting is permanent. Balances are derived from postings, never edited by hand.",
    points: ["Overdrafts rejected at posting time", "Pending, active, suspended and closed lifecycle"],
  },
  {
    icon: "pi-database",
    title: "Reference data",
    body: "Instruments, currencies and exchanges that enrich every trade, served from a cache so lookups stay fast.",
    points: ["Alternate identifiers per instrument", "Changes restricted to administrators"],
  },
  {
    icon: "pi-share-alt",
    title: "Allocation engine",
    body: "Consumes FIX allocation instructions, validates every field and allocates whole executions to the right accounts.",
    points: ["New, Replace and Cancel chains", "Settlement requested once allocated"],
  },
];

const STEPS = [
  {
    title: "Capture",
    body: "Orders and fills are entered in the console or through the trading API, each with its owner and account.",
  },
  {
    title: "Receive",
    body: "A FIX AllocationInstruction arrives on Kafka and is stored verbatim before any processing starts.",
  },
  {
    title: "Validate",
    body: "Every field error is collected at once, and a resend of the same allocation is recognised and absorbed.",
  },
  {
    title: "Allocate",
    body: "Whole executions are allocated to accounts under row locks, then settlement is requested by event.",
  },
];

const CONTROLS = [
  {
    icon: "pi-sync",
    title: "No double processing",
    body: "Business keys absorb resends, and events leave through a transactional outbox only after the commit.",
  },
  {
    icon: "pi-history",
    title: "Immutable history",
    body: "Ledger postings and execution amendments are appended, never overwritten.",
  },
  {
    icon: "pi-check-circle",
    title: "Every error at once",
    body: "Validation returns the complete list of problems with a message, not just the first one it meets.",
  },
  {
    icon: "pi-lock",
    title: "Safe under concurrency",
    body: "Row locks stop a correction and an allocation from changing the same execution at the same time.",
  },
  {
    icon: "pi-shield",
    title: "Tokens stay server-side",
    body: "Access tokens live in an encrypted, httpOnly session; the browser only talks to the console itself.",
  },
  {
    icon: "pi-eye-slash",
    title: "Need-to-know access",
    body: "Investors see only their own records. Another owner's record reads as not found, never as forbidden.",
  },
];

const ROLES = [
  {
    icon: "pi-user",
    name: "Investor",
    summary: "Works with their own orders and accounts.",
    can: [
      "Create, edit and delete orders while they are new",
      "Follow executions and their amendment chains",
      "Open accounts and read balances and ledgers",
    ],
  },
  {
    icon: "pi-briefcase",
    name: "Administrator",
    summary: "Runs the books across every owner.",
    can: [
      "Capture, correct and cancel executions",
      "Activate, suspend and close accounts",
      "Post cash and securities transactions",
      "Review instruments, currencies and exchanges",
    ],
  },
];

function SignInButton({
  action,
  label,
  className = "",
}: {
  action: SignInAction;
  label: string;
  className?: string;
}) {
  return (
    <form action={action} className="landing-signin-form">
      <button type="submit" className={`landing-btn landing-btn-primary ${className}`}>
        {label}
        <i className="pi pi-arrow-right" aria-hidden="true" />
      </button>
    </form>
  );
}

/** A static, illustrative slice of the executions console (decorative). */
function ConsolePreview() {
  const rows = [
    { id: "EXEC-2041", order: "ORD-1187", qty: "1,200.0000", px: "189.25000000", time: "2026-07-15 13:45:30", allocated: true },
    { id: "EXEC-2042", order: "ORD-1187", qty: "800.0000", px: "189.30000000", time: "2026-07-15 13:46:02", allocated: true },
    { id: "EXEC-2043", order: "ORD-1192", qty: "500.0000", px: "188.90000000", time: "2026-07-15 14:02:11", allocated: false },
    { id: "EXEC-2044", order: "ORD-1192", qty: "300.0000", px: "189.10000000", time: "2026-07-15 14:05:47", allocated: false },
  ];

  return (
    <div
      className="landing-preview"
      role="img"
      aria-label="Preview of the executions console: four executions, two allocated, with an allocation event confirming 2,000 shares allocated."
    >
      <div className="landing-preview-window" aria-hidden="true">
        <div className="landing-preview-bar">
          <div>
            <p className="landing-preview-title">Executions</p>
            <p className="landing-preview-sub">AAPL · Buy · trade date 2026-07-15</p>
          </div>
          <span className="landing-preview-action">
            <i className="pi pi-plus" /> Capture execution
          </span>
        </div>
        <table className="landing-preview-table">
          <thead>
            <tr>
              <th>Exec ID</th>
              <th className="wide">Order</th>
              <th className="num">Qty</th>
              <th className="num price">Price</th>
              <th className="wide">Time</th>
              <th>Allocation</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td className="id">{r.id}</td>
                <td className="id wide">{r.order}</td>
                <td className="num">{r.qty}</td>
                <td className="num price">{r.px}</td>
                <td className="wide">{r.time}</td>
                <td>
                  {r.allocated ? (
                    <span className="status-badge tone-success">
                      <i className="pi pi-check-circle" /> Allocated
                    </span>
                  ) : (
                    <span className="status-badge tone-neutral">
                      <i className="pi pi-circle" /> Unallocated
                    </span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {/* Carbon: pagination bottom-right, so the floating event below covers only empty space. */}
        <div className="landing-preview-pager">
          <span>1–4 of 4</span>
          <span className="landing-preview-page">1</span>
        </div>
      </div>

      <div className="landing-float landing-float-event" aria-hidden="true">
        <span className="landing-float-icon tone-success">
          <i className="pi pi-check" />
        </span>
        <div>
          <p className="landing-float-title">ExecutionsAllocated</p>
          <p className="landing-float-sub">ALLOC-7Q2 · 2 executions · 2,000.0000</p>
        </div>
      </div>
    </div>
  );
}

export function Landing({ signInAction }: { signInAction: SignInAction }) {
  return (
    <div className="landing">
      <a href="#landing-main" className="skip-link">
        Skip to main content
      </a>

      <header className="landing-header">
        <div className="landing-container landing-header-inner">
          <a href="#top" className="landing-brand" aria-label="Trading Capture System, back to top">
            <Image src="/logo.png" alt="" width={28} height={28} priority />
            <span>Trading Capture System</span>
          </a>
          <nav aria-label="Sections" className="landing-nav">
            <a href="#platform">Platform</a>
            <a href="#how-it-works">How it works</a>
            <a href="#controls">Controls</a>
            <a href="#roles">Roles</a>
          </nav>
          <SignInButton action={signInAction} label="Sign in" className="landing-btn-sm" />
        </div>
      </header>

      <main id="landing-main" tabIndex={-1}>
        <section className="landing-hero" id="top" aria-labelledby="landing-title">
          <div className="landing-container">
            <div className="landing-hero-copy">
              <p className="landing-eyebrow">
                <span className="landing-eyebrow-dot" aria-hidden="true" />
                Post-trade operations console
              </p>
              <h1 id="landing-title">
                Capture every trade. <span>Allocate it exactly once.</span>
              </h1>
              <p className="landing-lead">
                Orders, executions, accounts and FIX allocations in one place — backed by an immutable
                ledger, idempotent event processing and{" "}
                <span className="landing-nowrap">role-based</span> access.
              </p>
              <div className="landing-cta-row">
                <SignInButton action={signInAction} label="Sign in to the console" />
                <a href="#how-it-works" className="landing-btn landing-btn-secondary">
                  See how it works
                </a>
              </div>
              <p className="landing-note">
                <i className="pi pi-lock" aria-hidden="true" /> Single sign-on with your
                organisation&apos;s Keycloak account.
              </p>
            </div>
            <ConsolePreview />
          </div>
        </section>

        <section className="landing-facts" aria-label="Platform at a glance">
          <dl className="landing-container landing-facts-grid">
            {FACTS.map((f) => (
              <div key={f.value} className="landing-fact">
                <dt>{f.label}</dt>
                <dd>{f.value}</dd>
              </div>
            ))}
          </dl>
        </section>

        <section className="landing-section" id="platform" aria-labelledby="platform-title">
          <div className="landing-container">
            <div className="landing-section-head">
              <p className="landing-kicker">Platform</p>
              <h2 id="platform-title">One console for the whole post-trade flow</h2>
              <p>
                Four services, each owning its data, connected by versioned events. The console puts
                them behind a single sign-in.
              </p>
            </div>
            <div className="landing-cards">
              {PRODUCTS.map((p) => (
                <article key={p.title} className="landing-card">
                  <span className="landing-icon" aria-hidden="true">
                    <i className={`pi ${p.icon}`} />
                  </span>
                  <h3>{p.title}</h3>
                  <p>{p.body}</p>
                  <ul className="landing-checks">
                    {p.points.map((point) => (
                      <li key={point}>
                        <i className="pi pi-check" aria-hidden="true" />
                        {point}
                      </li>
                    ))}
                  </ul>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section
          className="landing-section landing-section-soft"
          id="how-it-works"
          aria-labelledby="how-title"
        >
          <div className="landing-container">
            <div className="landing-section-head">
              <p className="landing-kicker">How it works</p>
              <h2 id="how-title">From fill to settlement in four steps</h2>
              <p>Each step records what it did before the next one starts, so nothing is lost between them.</p>
            </div>
            <ol className="landing-steps">
              {STEPS.map((s, i) => (
                <li key={s.title} className="landing-step">
                  <span className="landing-step-num" aria-hidden="true">
                    {i + 1}
                  </span>
                  <h3>{s.title}</h3>
                  <p>{s.body}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="landing-section" id="controls" aria-labelledby="controls-title">
          <div className="landing-container landing-split">
            <div className="landing-section-head landing-section-head-left">
              <p className="landing-kicker">Controls</p>
              <h2 id="controls-title">Correct by construction</h2>
              <p>
                The guarantees live in the services, not in the screens: the console only shows what
                the books already enforce.
              </p>
            </div>
            <ul className="landing-controls">
              {CONTROLS.map((c) => (
                <li key={c.title}>
                  <span className="landing-icon landing-icon-sm" aria-hidden="true">
                    <i className={`pi ${c.icon}`} />
                  </span>
                  <div>
                    <h3>{c.title}</h3>
                    <p>{c.body}</p>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section
          className="landing-section landing-section-soft"
          id="roles"
          aria-labelledby="roles-title"
        >
          <div className="landing-container">
            <div className="landing-section-head">
              <p className="landing-kicker">Roles</p>
              <h2 id="roles-title">The right controls for each person</h2>
              <p>What you can do follows your role and what you own, checked by every service.</p>
            </div>
            <div className="landing-roles">
              {ROLES.map((r) => (
                <article key={r.name} className="landing-card landing-role">
                  <div className="landing-role-head">
                    <span className="landing-icon" aria-hidden="true">
                      <i className={`pi ${r.icon}`} />
                    </span>
                    <div>
                      <h3>{r.name}</h3>
                      <p>{r.summary}</p>
                    </div>
                  </div>
                  <ul className="landing-checks">
                    {r.can.map((item) => (
                      <li key={item}>
                        <i className="pi pi-check" aria-hidden="true" />
                        {item}
                      </li>
                    ))}
                  </ul>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className="landing-section" aria-labelledby="cta-title">
          <div className="landing-container">
            <div className="landing-cta">
              <h2 id="cta-title">Open the console</h2>
              <p>Sign in with your organisation account to pick up where the books left off.</p>
              <SignInButton action={signInAction} label="Sign in" />
            </div>
          </div>
        </section>
      </main>

      <footer className="landing-footer">
        <div className="landing-container landing-footer-inner">
          <div className="landing-brand landing-brand-quiet">
            <Image src="/logo.png" alt="" width={20} height={20} />
            <span>Trading Capture System</span>
          </div>
          <p>Internal post-trade operations console.</p>
        </div>
      </footer>
    </div>
  );
}
