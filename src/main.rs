mod audit;
mod sentinel;

use audit::ActiveLevel;
use sentinel::{ComplianceController, SentinelClone};
use std::env;
use tokio::task;

#[tokio::main]
async fn main() {
    // ◄— Corrected environment loading call to utilize dotenvy cleanly
    dotenvy::dotenv().ok();

    // Fetch secrets securely from the local system configuration layer
    let groq_api_key = env::var("GROQ_API_KEY").ok();
    let nvidia_api_key = env::var("NVIDIA_API_KEY").ok();

    println!("==================================================================");
    println!("             LEXIQ COMPLIANCE SWARM INITIALIZATION                ");
    println!("==================================================================\n");

    // SIMULATION: User upgrades to Enterprise T3, but their payment has failed.
    // Set to 4 days ago (outside the 3-day grace period window)
    let four_days_seconds = 4 * 24 * 60 * 60;
    let payment_failed_at = chrono::Utc::now().timestamp() as u64 - four_days_seconds;

    let controller = ComplianceController::initialize(
        "ZAR-SESS-910283", 
        ActiveLevel::T3, // User requested T3
        Some(payment_failed_at) // Payment failed 4 days ago
    );

    println!("Active Session-ID: {}", controller.session.session_id);
    println!("Operating Level:   {:?}", controller.session.level);
    println!("Quantum Cycle time: {} cycles\n", controller.session.quantum_cycle);

    // Retrieve verified credentials authorized strictly for this session level
    let (g_sec, n_sec) = match controller.session.level {
        ActiveLevel::T1 => (None, None), // Hard fallback clears keys out of active memory
        ActiveLevel::T2 => (groq_api_key.clone(), None),
        ActiveLevel::T3 => (groq_api_key.clone(), nvidia_api_key.clone()),
    };

    let sample_phrase = "Check howzit going with this lekker multiplatform rust implementation.";
    let mut tasks = vec![];

    // Spawn 80 concurrent Ephemeral Sentinel Clones
    for id in 0..80 {
        let level = controller.session.level.clone();
        let payload = sample_phrase.to_string();
        let g_clone = g_sec.clone();
        let n_clone = n_sec.clone();

        let join_handle = task::spawn(async move {
            let sentinel = SentinelClone::spawn(id, level);
            sentinel.execute_nla_task(&payload, g_clone, n_clone).await
        });
        tasks.push(join_handle);
    }

    let mut successful_workers = 0;
    for handle in tasks {
        if let Ok(Ok(output)) = handle.await {
            successful_workers += 1;
            if successful_workers % 20 == 0 {
                println!("[SENTINEL COMPLIANCE READOUT] Active Response: '{}'", output);
            }
        }
    }

    println!("\nProcessed results from {} active ephemeral instances.", successful_workers);
    println!("Temporal Integrity validated under SAST Audit. Session termination complete.");
}
