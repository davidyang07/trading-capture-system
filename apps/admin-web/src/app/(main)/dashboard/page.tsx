import ChartOne from "./(admin)/ChartOne";
import ChartThree from "./(admin)/ChartThree";
import ChartTwo from "./(admin)/ChartTwo";
import "./page.scss";

export default async function DashboardPage() {
  const role = process.env.APP_ROLE;

  const getImgCards = () => {
    const imgs = [];
    for (let i = 1; i <= 10; i++) {
      imgs.push(
        <div
          key={i}
          className="dashboard-img-box"
          style={{ backgroundImage: `url(/pic_${i}.png)` }}
        ></div>
      );
    }
    return imgs;
  };

  return (
    <>
      <div className="dashboard-imgs-box mb-4 grid grid-cols-5 gap-5">
        {getImgCards()}
      </div>

      {role === "admin" && (
        <>
          <div className="dashboard-mid main-card-light mb-4">
            <div className="main-title-container mb-8">
              <p className="main-title">Unmatching Orders</p>
            </div>
          </div>

          <div className="dashboard-foot">
            <div className="main-card-light">
              <div className="main-title-container mb-8">
                <div className="main-title">
                  <span>Orders</span>
                  <>
                    <span className="main-title-desc">
                      <span style={{ color: "#883cae" }}>50 </span>
                      Orders this Week
                    </span>
                  </>
                </div>
              </div>
              <ChartOne />
            </div>

            <div className="main-card-light">
              <div className="main-title-container mb-8">
                <div className="main-title">
                  <span>Order Matchings</span>
                  <>
                    <span className="main-title-desc">
                      <span style={{ color: "#883cae" }}>50 </span>
                      Executions and
                      <span style={{ color: "#883cae" }}>40 </span>
                      Allocations this Week
                    </span>
                  </>
                </div>
              </div>
              <ChartTwo />
            </div>

            <div className="main-card-light">
              <div className="main-title-container mb-8">
                <div className="main-title">
                  <span>Investors</span>
                  <>
                    <span className="main-title-desc">
                      <span style={{ color: "#883cae" }}>50 </span>
                      Investors this Month
                    </span>
                  </>
                </div>
              </div>
              <ChartThree />
            </div>
          </div>
        </>
      )}
    </>
  );
}
