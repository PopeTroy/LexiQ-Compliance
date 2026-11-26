use chrono::{DateTime, FixedOffset, Timelike, Utc};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub enum ActiveLevel {
    T1, // Core: Local Only (Zero Cloud Cost)
    T2, // Pro: Standard (Groq AI)
    T3, // Enterprise: Premium (Groq + NVIDIA NIM + ProQuest)
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub enum SessionAccessState {
    Active,
    GracePeriod,
    DowngradedToT1,
    FreeTierDisabled,
}

#[derive(Debug, Clone, Serialize)]
pub struct QuantumSession {
    pub session_id: String,
    pub level: ActiveLevel,
    pub access_state: SessionAccessState,
    pub timestamp_sast: String,
    pub quantum_cycle: u64,
    pub estimated_cost_units: f64,
}

impl QuantumSession {
    /// Constructs an active user session, mapping the temporal clock and tracking suffix
    pub fn new(base_id: &str, level: ActiveLevel) -> Self {
        Self::with_state(base_id, level, SessionAccessState::Active, 0.0)
    }

    pub fn with_state(
        base_id: &str,
        level: ActiveLevel,
        access_state: SessionAccessState,
        estimated_cost_units: f64,
    ) -> Self {
        let suffix = match level {
            ActiveLevel::T1 => "T1",
            ActiveLevel::T2 => "T2",
            ActiveLevel::T3 => "T3",
        };
        let session_id = format!("{}-{}", base_id, suffix);

        // Calculate South African Standard Time (SAST = UTC+2)
        let utc_now: DateTime<Utc> = Utc::now();
        let sast_offset = FixedOffset::east_opt(2 * 3600).unwrap();
        let sast_now = utc_now.with_timezone(&sast_offset);

        // 1:6000 Quantum Dilation: Maps 1 second to 24 cycles
        let seconds_today = sast_now.time().num_seconds_from_midnight() as u64;
        let quantum_cycle = seconds_today * 24;

        QuantumSession {
            session_id,
            level,
            access_state,
            timestamp_sast: sast_now.to_rfc3339(),
            quantum_cycle,
            estimated_cost_units,
        }
    }
}

pub fn health_summary() -> String {
    let has_groq = std::env::var("GROQ_API_KEY")
        .map(|v| !v.trim().is_empty())
        .unwrap_or(false);
    let has_nim = std::env::var("NVIDIA_API_KEY")
        .map(|v| !v.trim().is_empty())
        .unwrap_or(false);
    let free_tier_disabled = std::env::var("EMERGENCY_DISABLE_FREE_TIER")
        .map(|v| v.eq_ignore_ascii_case("true") || v == "1")
        .unwrap_or(false);

    let session = QuantumSession::new("LEXI-Q", ActiveLevel::T3);
    format!(
        "Audit ready: {} ({:?}, state={:?}) @ {} [cycle={}] | groq={} nim={} free_tier_kill_switch={}",
        session.session_id,
        session.level,
        session.access_state,
        session.timestamp_sast,
        session.quantum_cycle,
        has_groq,
        has_nim,
        free_tier_disabled
    )
}
