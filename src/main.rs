use crate::audit::{health_summary, ActiveLevel};
use crate::sentinel::{ComplianceController, SentinelClone};

#[tokio::main]
async fn main() {
    let status = health_summary();
    println!("{status}");

    let controller = ComplianceController::initialize("LEXI-Q", ActiveLevel::T3, None);
    let sentinel = SentinelClone::spawn(1, controller.session.level);
    let groq_key = std::env::var("GROQ_API_KEY").ok();
    let nim_key = std::env::var("NVIDIA_API_KEY").ok();

    if nim_key.is_none() && groq_key.is_none() {
        let local = SentinelClone::spawn(1, ActiveLevel::T1);
        if let Ok(out) = local.execute_nla_task("telemetry probe", None, None).await {
            println!("{out}");
        }
    } else if let Ok(out) = sentinel.execute_nla_task("telemetry probe", groq_key, nim_key).await {
        println!("{out}");
    }
}
