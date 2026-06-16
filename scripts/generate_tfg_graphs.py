"""
Generates publication-quality graphs for the Dosius Smart TFG document.
Reproduces the exact math from the Kotlin physiology engine.

Usage:
    python scripts/generate_tfg_graphs.py

Output:
    scripts/output/*.png  (300 DPI, transparent background)
"""

import os
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch
from scipy.stats import norm

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "output")
os.makedirs(OUTPUT_DIR, exist_ok=True)

COLORS = {
    "primary": "#1565C0",
    "secondary": "#E91E63",
    "accent": "#FF9800",
    "green": "#4CAF50",
    "red": "#F44336",
    "purple": "#7B1FA2",
    "teal": "#00897B",
    "grey": "#9E9E9E",
    "bg_normal": "#4CAF50",
    "bg_high": "#FF9800",
    "bg_very_high": "#B71C1C",
    "bg_low": "#F44336",
    "bg_very_low": "#B71C1C",
}

def setup_style():
    plt.rcParams.update({
        "font.family": "serif",
        "font.size": 11,
        "axes.titlesize": 13,
        "axes.labelsize": 12,
        "legend.fontsize": 10,
        "xtick.labelsize": 10,
        "ytick.labelsize": 10,
        "figure.dpi": 300,
        "savefig.dpi": 300,
        "savefig.bbox": "tight",
        "savefig.transparent": True,
        "axes.grid": True,
        "grid.alpha": 0.3,
        "axes.spines.top": False,
        "axes.spines.right": False,
    })

# ─── Insulin model (exact port from InsulinCurve.kt) ─────────────────────────

def iob_fraction(elapsed_min, peak=75.0, dia=300.0):
    end = max(dia, 300.0)
    if elapsed_min <= 0:
        return 1.0
    if elapsed_min >= end:
        return 0.0
    tp = np.clip(peak, 35.0, 120.0)
    tau = tp * (1 - tp / end) / (1 - 2 * tp / end)
    a = 2 * tau / end
    s = 1.0 / (1 - a + (1 + a) * np.exp(-end / tau))
    t = float(elapsed_min)
    iob = 1.0 - s * (1 - a) * (
        (t * t / (tau * end * (1 - a)) - t / tau - 1) * np.exp(-t / tau) + 1
    )
    return np.clip(iob, 0.0, 1.0)

iob_fraction_v = np.vectorize(iob_fraction)

def basal_iob_fraction(elapsed_min, dia_hours=24.0):
    return max(0.0, 1.0 - elapsed_min / (dia_hours * 60.0))

basal_iob_fraction_v = np.vectorize(basal_iob_fraction)

def insulin_activity(elapsed_min, peak=75.0, dia=300.0, dt=1.0):
    iob_now = iob_fraction(elapsed_min, peak, dia)
    iob_next = iob_fraction(elapsed_min + dt, peak, dia)
    return -(iob_next - iob_now) / dt

insulin_activity_v = np.vectorize(insulin_activity)


# ─── 1. IOB Curve: Fiasp + 3 anonymous DIA variants ────────────────────────

def plot_iob_curves():
    extra_peaks = [70, 85, 100]
    extra_colors = [COLORS["teal"], COLORS["purple"], COLORS["grey"]]
    extra_styles = ["--", "-.", ":"]

    t = np.linspace(0, 300, 600)

    fig, ax = plt.subplots(figsize=(8, 4.5))

    # Anonymous peak curves first (behind Fiasp)
    for peak, color, ls in zip(extra_peaks, extra_colors, extra_styles):
        ax.plot(t, iob_fraction_v(t, peak=peak, dia=300),
                color=color, linewidth=1.5, linestyle=ls,
                label=f"Pico = {peak} min", alpha=0.8)

    # Fiasp on top
    ax.plot(t, iob_fraction_v(t, peak=55, dia=300),
            color=COLORS["secondary"], linewidth=2.5, label="Fiasp (pico = 55 min)")

    ax.axvline(55, color=COLORS["secondary"], alpha=0.3, linestyle=":")
    ax.annotate("Pico Fiasp (55 min)", xy=(55, iob_fraction(55, 55, 300)),
                xytext=(90, 0.82), fontsize=9, color=COLORS["secondary"],
                arrowprops=dict(arrowstyle="->", color=COLORS["secondary"], alpha=0.5))

    ax.set_xlabel("Tiempo desde la dosis (min)")
    ax.set_ylabel("Fraccion IOB restante")
    ax.set_title("Curva de insulina a bordo (IOB) - Modelo exponencial OpenAPS")
    ax.legend(loc="upper right")
    ax.set_xlim(0, 300)
    ax.set_ylim(0, 1.05)

    fig.savefig(os.path.join(OUTPUT_DIR, "01_iob_bolus_curves.png"))
    plt.close(fig)
    print("  [1/10] IOB bolus curves")


# ─── 2. Insulin Activity Curve ───────────────────────────────────────────────

def plot_insulin_activity():
    extra_peaks = [70, 85, 100]
    extra_colors = [COLORS["teal"], COLORS["purple"], COLORS["grey"]]
    extra_styles = ["--", "-.", ":"]

    t = np.linspace(1, 300, 600)

    fig, ax = plt.subplots(figsize=(8, 4.5))

    # Anonymous peak curves first
    for peak, color, ls in zip(extra_peaks, extra_colors, extra_styles):
        act = insulin_activity_v(t, peak=peak, dia=300)
        ax.plot(t, act, color=color, linewidth=1.5, linestyle=ls,
                label=f"Pico = {peak} min", alpha=0.8)

    # Fiasp on top with fill
    act_fiasp = insulin_activity_v(t, peak=55, dia=300)
    ax.fill_between(t, act_fiasp, alpha=0.15, color=COLORS["secondary"])
    ax.plot(t, act_fiasp, color=COLORS["secondary"], linewidth=2.5,
            label="Fiasp (pico = 55 min)")

    peak_idx = np.argmax(act_fiasp)
    ax.plot(t[peak_idx], act_fiasp[peak_idx], "o",
            color=COLORS["secondary"], markersize=7)

    ax.set_xlabel("Tiempo desde la dosis (min)")
    ax.set_ylabel("Actividad insulínica (U/min, normalizada)")
    ax.set_title("Curva de actividad insulínica (derivada negativa de IOB)")
    ax.legend(loc="upper right")
    ax.set_xlim(0, 300)
    ax.set_ylim(bottom=0)

    fig.savefig(os.path.join(OUTPUT_DIR, "02_insulin_activity.png"))
    plt.close(fig)
    print("  [2/10] Insulin activity curves")


# ─── 3. IOB with multiple doses (stacking scenario) ─────────────────────────

def plot_iob_stacking():
    t = np.linspace(0, 480, 600)
    dose_times = [0, 60, 180]
    dose_units = [4.0, 2.0, 3.0]
    dose_labels = ["Bolo 4U (t=0)", "Bolo 2U (t=60)", "Bolo 3U (t=180)"]
    colors_doses = [COLORS["primary"], COLORS["secondary"], COLORS["accent"]]

    contributions = []
    for dt, units in zip(dose_times, dose_units):
        elapsed = t - dt
        iob = np.where(elapsed >= 0, iob_fraction_v(elapsed, 75, 300) * units, 0.0)
        contributions.append(iob)

    total = sum(contributions)

    fig, ax = plt.subplots(figsize=(8, 4.5))
    ax.stackplot(t, *contributions, labels=dose_labels,
                 colors=colors_doses, alpha=0.4)
    ax.plot(t, total, color="black", linewidth=2.5, label="IOB total")

    for dt, label, c in zip(dose_times, dose_labels, colors_doses):
        ax.axvline(dt, color=c, alpha=0.4, linestyle=":")

    ax.set_xlabel("Tiempo (min)")
    ax.set_ylabel("Insulina a bordo (U)")
    ax.set_title("Apilamiento de IOB con múltiples bolos")
    ax.legend(loc="upper right")
    ax.set_xlim(0, 480)
    ax.set_ylim(bottom=0)

    fig.savefig(os.path.join(OUTPUT_DIR, "03_iob_stacking.png"))
    plt.close(fig)
    print("  [3/10] IOB stacking")


# ─── 4. Basal IOB decay ─────────────────────────────────────────────────────

def plot_basal_iob():
    t = np.linspace(0, 1500, 500)
    iob = basal_iob_fraction_v(t, dia_hours=24.0)

    fig, ax = plt.subplots(figsize=(8, 4))
    ax.plot(t / 60, iob, color=COLORS["teal"], linewidth=2)
    ax.fill_between(t / 60, iob, alpha=0.1, color=COLORS["teal"])
    ax.set_xlabel("Tiempo desde la dosis (horas)")
    ax.set_ylabel("Fraccion IOB restante")
    ax.set_title("Curva IOB basal (decaimiento lineal, DIA = 24h)")
    ax.set_xlim(0, 25)
    ax.set_ylim(0, 1.05)

    fig.savefig(os.path.join(OUTPUT_DIR, "04_basal_iob.png"))
    plt.close(fig)
    print("  [4/10] Basal IOB")


# ─── 5. COB dynamic absorption model ─────────────────────────────────────────

def plot_cob_absorption():
    MIN_ABS = 2.0  # g per 5 min (from CarbAbsorptionModel.kt: MIN_ABSORPTION_PER_5MIN = 2f)
    icr, isf = 10.0, 50.0
    initial_carbs = 60.0

    rng = np.random.default_rng(42)
    # High-GI: large deviations that clearly exceed the floor (absorbed = dev*ICR/ISF)
    # dev*10/50 = dev*0.2, so dev=30 -> absorbed=6 g/5min (well above floor of 2)
    deviations_high = rng.normal(30.0, 8.0, 60)
    # Low-GI: small deviations that mostly fall below the floor
    deviations_low = rng.normal(4.0, 2.0, 60)

    def simulate_cob(deviations):
        cob = initial_carbs
        history = [cob]
        absorbed_per_step = []
        for dev in deviations:
            absorbed = max(MIN_ABS, dev * icr / isf)
            absorbed = min(absorbed, cob)
            cob = max(0, cob - absorbed)
            history.append(cob)
            absorbed_per_step.append(absorbed)
        return history, absorbed_per_step

    t_steps = np.arange(0, 61) * 5
    cob_high, abs_high = simulate_cob(deviations_high)
    cob_low, abs_low = simulate_cob(deviations_low)

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 4.5))

    ax1.plot(t_steps, cob_high, color=COLORS["primary"], linewidth=2,
             label="Desviacion alta (comida GI alto)")
    ax1.plot(t_steps, cob_low, color=COLORS["accent"], linewidth=2,
             label="Desviacion baja (comida GI bajo)")
    ax1.axhline(0, color="grey", linewidth=0.5)
    ax1.set_xlabel("Tiempo desde la comida (min)")
    ax1.set_ylabel("COB restante (g)")
    ax1.set_title("Carbohidratos a bordo (COB)")
    ax1.legend()
    ax1.set_xlim(0, 300)
    ax1.set_ylim(bottom=0)

    t_abs = np.arange(0, 60) * 5
    ax2.bar(t_abs, abs_high, width=4, alpha=0.6, color=COLORS["primary"],
            label="GI alto")
    ax2.bar(t_abs, abs_low, width=4, alpha=0.6, color=COLORS["accent"],
            label="GI bajo")
    ax2.axhline(MIN_ABS, color=COLORS["red"], linestyle="--", linewidth=1.5,
                label=f"Absorcion minima ({MIN_ABS} g/5min)")
    ax2.set_xlabel("Tiempo desde la comida (min)")
    ax2.set_ylabel("Absorcion por paso (g/5min)")
    ax2.set_title("Tasa de absorcion dinamica")
    ax2.legend(fontsize=9)
    ax2.set_xlim(0, 300)
    ax2.set_ylim(bottom=0)

    fig.tight_layout()
    fig.savefig(os.path.join(OUTPUT_DIR, "05_cob_absorption.png"))
    plt.close(fig)
    print("  [5/10] COB absorption")


# ─── 6. Glucose forecast (4-component) ──────────────────────────────────────

def plot_glucose_forecast():
    bg_now = 150.0
    isf, icr = 50.0, 10.0
    dia = 300.0
    initial_iob_units = 3.0
    initial_cob = 40.0
    slope = 0.5  # mg/dL/min (slight rise)
    mean_dev = 2.0  # mg/dL/5min retrospective correction

    # Exercise event: starts now (offset=0), 60-min moderate workout, 40 mg/dL/h expected drop
    ex_start_min = 0.0
    ex_duration_min = 60.0
    ex_drop_per_hour = 40.0
    ex_end_min = ex_start_min + ex_duration_min

    n_steps = 48
    t = np.arange(1, n_steps + 1) * 5

    iob_now = initial_iob_units * iob_fraction(0, 75, dia)
    cob_per_step = initial_cob / 24.0

    insulin_effect = np.zeros(n_steps)
    carb_effect = np.zeros(n_steps)
    momentum = np.zeros(n_steps)
    retro = np.zeros(n_steps)
    exercise_effect = np.zeros(n_steps)
    predicted_bg = np.zeros(n_steps)

    cum_momentum = 0.0
    cum_retro = 0.0
    cum_exercise = 0.0

    for i in range(n_steps):
        step = i + 1
        step_start = (step - 1) * 5.0
        step_end = step * 5.0
        future_iob = initial_iob_units * iob_fraction(step * 5, 75, dia)
        future_cob = max(0, initial_cob - step * cob_per_step)

        insulin_effect[i] = (future_iob - iob_now) * isf
        carb_effect[i] = (initial_cob - future_cob) * (isf / icr)

        cum_momentum += slope * 5 * max(0, 1 - step * 5 / 20.0)
        momentum[i] = cum_momentum

        cum_retro += mean_dev * max(0, 1 - step * 5 / 60.0)
        retro[i] = cum_retro

        # Active minutes of exercise within this 5-min step, clipped to window
        active_min = max(0.0, min(step_end, ex_end_min) - max(step_start, ex_start_min))
        cum_exercise += ex_drop_per_hour * active_min / 60.0
        exercise_effect[i] = cum_exercise

        predicted_bg[i] = np.clip(
            bg_now + insulin_effect[i] + carb_effect[i] + momentum[i] + retro[i] - cum_exercise,
            40, 400
        )

    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(10, 8), height_ratios=[2, 1])

    ax1.axhspan(70, 180, alpha=0.08, color=COLORS["green"], label="Rango objetivo (70-180)")
    ax1.axhline(180, color=COLORS["accent"], alpha=0.3, linewidth=0.8, linestyle="--")
    ax1.axhline(70, color=COLORS["red"], alpha=0.3, linewidth=0.8, linestyle="--")

    ax1.plot([0], [bg_now], "o", color="black", markersize=8, zorder=5)
    ax1.plot(t, predicted_bg, color="black", linewidth=2.5, label="Glucosa predicha", zorder=4)

    ax1.set_xlabel("Minutos en el futuro")
    ax1.set_ylabel("Glucosa (mg/dL)")
    ax1.set_title(
        f"Prediccion de glucosa a 4 horas "
        f"(BG={int(bg_now)}, IOB={initial_iob_units}U, COB={int(initial_cob)}g, "
        f"ejercicio {int(ex_duration_min)} min)"
    )
    ax1.legend(loc="upper right")
    ax1.set_xlim(0, 240)
    ax1.set_ylim(40, 280)

    ax2.plot(t, insulin_effect, color=COLORS["primary"], linewidth=1.8,
             label=f"Efecto insulina (IOB={initial_iob_units}U)")
    ax2.plot(t, carb_effect, color=COLORS["accent"], linewidth=1.8,
             label=f"Efecto carbohidratos (COB={int(initial_cob)}g)")
    ax2.plot(t, momentum, color=COLORS["purple"], linewidth=1.8,
             label="Momentum (decae en 20 min)")
    ax2.plot(t, retro, color=COLORS["teal"], linewidth=1.8,
             label="Correccion retrospectiva (decae en 60 min)")
    ax2.plot(t, -exercise_effect, color=COLORS["red"], linewidth=1.8,
             label=f"Efecto ejercicio ({int(ex_drop_per_hour)} mg/dL/h, {int(ex_duration_min)} min)")
    ax2.axhline(0, color="grey", linewidth=0.5)

    ax2.set_xlabel("Minutos en el futuro")
    ax2.set_ylabel("Contribucion (mg/dL)")
    ax2.set_title("Componentes individuales de la prediccion")
    ax2.legend(loc="upper right", fontsize=9)
    ax2.set_xlim(0, 240)

    fig.tight_layout()
    fig.savefig(os.path.join(OUTPUT_DIR, "06_glucose_forecast.png"))
    plt.close(fig)
    print("  [6/10] Glucose forecast")


# ─── 7. Bayesian conjugate update (ISF example) ─────────────────────────────

def plot_bayesian_update():
    prior_mean, prior_var = 50.0, 100.0
    obs_mean, obs_var = 42.0, 9.0
    n_obs = 5

    post_var = 1.0 / (1.0 / prior_var + n_obs / obs_var)
    post_mean = post_var * (prior_mean / prior_var + n_obs * obs_mean / obs_var)

    x = np.linspace(20, 80, 500)
    prior_pdf = norm.pdf(x, prior_mean, np.sqrt(prior_var))
    likelihood_pdf = norm.pdf(x, obs_mean, np.sqrt(obs_var / n_obs))
    post_pdf = norm.pdf(x, post_mean, np.sqrt(post_var))

    # Normalize for visual comparison
    prior_pdf /= prior_pdf.max()
    likelihood_pdf /= likelihood_pdf.max()
    post_pdf /= post_pdf.max()

    fig, ax = plt.subplots(figsize=(8, 4.5))

    ax.fill_between(x, prior_pdf, alpha=0.15, color=COLORS["grey"])
    ax.plot(x, prior_pdf, color=COLORS["grey"], linewidth=2,
            label=f"Prior ($\\mu$={prior_mean}, $\\sigma^2$={prior_var})")

    ax.fill_between(x, likelihood_pdf, alpha=0.15, color=COLORS["accent"])
    ax.plot(x, likelihood_pdf, color=COLORS["accent"], linewidth=2,
            label=f"Verosimilitud (obs={obs_mean}, n={n_obs})")

    ax.fill_between(x, post_pdf, alpha=0.2, color=COLORS["primary"])
    ax.plot(x, post_pdf, color=COLORS["primary"], linewidth=2.5,
            label=f"Posterior ($\\mu$={post_mean:.1f}, $\\sigma^2$={post_var:.1f})")

    ax.axvline(prior_mean, color=COLORS["grey"], alpha=0.4, linestyle=":")
    ax.axvline(obs_mean, color=COLORS["accent"], alpha=0.4, linestyle=":")
    ax.axvline(post_mean, color=COLORS["primary"], alpha=0.4, linestyle=":")

    ax.set_xlabel("ISF (mg/dL por U)")
    ax.set_ylabel("Densidad (normalizada)")
    ax.set_title("Actualizacion bayesiana conjugada gaussiana del ISF")
    ax.legend(loc="upper right")
    ax.set_ylim(bottom=0)

    fig.savefig(os.path.join(OUTPUT_DIR, "07_bayesian_update.png"))
    plt.close(fig)
    print("  [7/10] Bayesian update")


# ─── 8. Mixture rule weights ─────────────────────────────────────────────────

def plot_mixture_rule():
    fig, axes = plt.subplots(1, 3, figsize=(14, 4))

    # Factor A vs RMSE
    rmse = np.linspace(0, 60, 200)
    factor_a = np.exp(-rmse / 20.0)
    axes[0].plot(rmse, factor_a, color=COLORS["primary"], linewidth=2)
    axes[0].fill_between(rmse, factor_a, alpha=0.1, color=COLORS["primary"])
    axes[0].set_xlabel("RMSE (mg/dL/5min)")
    axes[0].set_ylabel("Factor A")
    axes[0].set_title("Factor A = exp(-RMSE/20)")
    axes[0].set_ylim(0, 1.05)
    axes[0].axhline(0.5, color=COLORS["grey"], linestyle="--", alpha=0.5)
    axes[0].annotate("RMSE=14 -> A=0.5", xy=(14, 0.5), xytext=(25, 0.65),
                     fontsize=9, arrowprops=dict(arrowstyle="->", color=COLORS["grey"]))

    # Factor B ISF vs posterior std
    post_std = np.linspace(0, 40, 200)
    factor_b_isf = np.exp(-post_std / 15.0)
    factor_b_icr = np.exp(-post_std / 2.0)
    axes[1].plot(post_std, factor_b_isf, color=COLORS["primary"], linewidth=2,
                 label="ISF (escala=15)")
    axes[1].plot(post_std, factor_b_icr, color=COLORS["secondary"], linewidth=2,
                 linestyle="--", label="ICR (escala=2)")
    axes[1].set_xlabel("$\\sigma_{posterior}$ promedio")
    axes[1].set_ylabel("Factor B")
    axes[1].set_title("Factor B = exp($-\\sigma_{post}$/escala)")
    axes[1].set_ylim(0, 1.05)
    axes[1].legend()

    # Combined weight w = A * B
    rmse_grid = np.linspace(0, 40, 100)
    std_grid = np.linspace(0, 20, 100)
    R, S = np.meshgrid(rmse_grid, std_grid)
    W = np.exp(-R / 20.0) * np.exp(-S / 15.0)

    im = axes[2].contourf(R, S, W, levels=20, cmap="Blues")
    axes[2].set_xlabel("RMSE (mg/dL/5min)")
    axes[2].set_ylabel("$\\sigma_{posterior}$ ISF")
    axes[2].set_title("Peso w = Factor A $\\times$ Factor B")
    plt.colorbar(im, ax=axes[2], label="w")

    fig.tight_layout()
    fig.savefig(os.path.join(OUTPUT_DIR, "08_mixture_rule.png"))
    plt.close(fig)
    print("  [8/10] Mixture rule")


# ─── 9. Confidence system ────────────────────────────────────────────────────

def plot_confidence_system():
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 4.5))

    # paramConfidence(nObs) = min(100, 30 + nObs * 10)
    n_obs = np.arange(0, 15)
    conf = np.minimum(100, 30 + n_obs * 10)

    ax1.step(n_obs, conf, where="post", color=COLORS["primary"], linewidth=2)
    ax1.fill_between(n_obs, conf, step="post", alpha=0.1, color=COLORS["primary"])
    ax1.axhline(100, color=COLORS["green"], linestyle="--", alpha=0.5)
    ax1.axhline(30, color=COLORS["red"], linestyle="--", alpha=0.5)

    ax1.annotate("Semilla (sin datos)", xy=(0, 30), xytext=(2, 20),
                 fontsize=9, color=COLORS["red"],
                 arrowprops=dict(arrowstyle="->", color=COLORS["red"]))
    ax1.annotate("100% a n=7", xy=(7, 100), xytext=(9, 85),
                 fontsize=9, color=COLORS["green"],
                 arrowprops=dict(arrowstyle="->", color=COLORS["green"]))

    ax1.set_xlabel("Numero de observaciones (n)")
    ax1.set_ylabel("Confianza del parametro (%)")
    ax1.set_title("paramConfidence(n) = min(100, 30 + n$\\times$10)")
    ax1.set_xlim(0, 14)
    ax1.set_ylim(0, 110)

    # Recommendation confidence = min(carbConf, isfConf, icrConf)
    categories = ["Prandial\n(carb=CERTAIN,\nn_ISF=7, n_ICR=5)",
                   "Prandial\n(carb=LOW,\nn_ISF=7, n_ICR=7)",
                   "Correccion\n(n_ISF=3,\nn_ICR=3)",
                   "Correccion\n(n_ISF=0,\nn_ICR=0)"]
    carb_confs = [100, 30, 100, 100]
    isf_confs  = [100, 100, 60, 30]
    icr_confs  = [80, 100, 60, 30]
    final_confs = [min(c, i, k) for c, i, k in zip(carb_confs, isf_confs, icr_confs)]

    x_pos = np.arange(len(categories))
    width = 0.2

    bars_carb = ax2.bar(x_pos - width, carb_confs, width, label="Conf. carbs", color=COLORS["accent"], alpha=0.7)
    bars_isf = ax2.bar(x_pos, isf_confs, width, label="Conf. ISF", color=COLORS["primary"], alpha=0.7)
    bars_icr = ax2.bar(x_pos + width, icr_confs, width, label="Conf. ICR", color=COLORS["teal"], alpha=0.7)

    for i, fc in enumerate(final_confs):
        ax2.plot(i, fc, "D", color=COLORS["red"], markersize=10, zorder=5)
    ax2.plot([], [], "D", color=COLORS["red"], markersize=8, label="Conf. final = min(...)")

    ax2.set_xticks(x_pos)
    ax2.set_xticklabels(categories, fontsize=8)
    ax2.set_ylabel("Confianza (%)")
    ax2.set_title("Confianza de la recomendacion = min(componentes)")
    ax2.legend(loc="upper right", fontsize=9)
    ax2.set_ylim(0, 115)

    fig.tight_layout()
    fig.savefig(os.path.join(OUTPUT_DIR, "09_confidence_system.png"))
    plt.close(fig)
    print("  [9/10] Confidence system")


# ─── 10. Prandial bolus formula components ───────────────────────────────────

def plot_prandial_components():
    isf, icr, target = 50.0, 10.0, 110.0
    carbs = 60.0
    current_bg = 180.0
    iob = 1.5
    trend_rate = 0.8  # mg/dL/min (rising)

    carb_dose = carbs / icr
    correction = (current_bg - target) / isf
    iob_offset = -iob
    trend_adj = np.clip(trend_rate * 0.1, -1, 1)
    raw_total = carb_dose + correction + iob_offset + trend_adj
    final = max(0, raw_total)

    # Waterfall chart: each bar starts where the previous one ended
    names = [
        f"Carbs/ICR\n{carbs:.0f}g/{icr:.0f}",
        f"Correccion\n({int(current_bg)}-{int(target)})/{int(isf)}",
        f"IOB\n-{iob}U",
        f"Tendencia\n{trend_rate}*0.1",
        "Bolo final",
    ]
    values = [carb_dose, correction, iob_offset, trend_adj, final]
    bar_colors = [COLORS["accent"], COLORS["red"], COLORS["primary"], COLORS["purple"], COLORS["green"]]

    # Compute bottoms for waterfall
    running = 0.0
    bottoms = []
    for i, v in enumerate(values):
        if i == len(values) - 1:  # total bar starts at 0
            bottoms.append(0.0)
        elif v >= 0:
            bottoms.append(running)
            running += v
        else:
            running += v
            bottoms.append(running)

    fig, ax = plt.subplots(figsize=(10, 5))
    x = np.arange(len(names))

    for i in range(len(names)):
        ax.bar(x[i], abs(values[i]), bottom=bottoms[i], color=bar_colors[i],
               alpha=0.8, edgecolor="white", linewidth=2, width=0.55)
        mid = bottoms[i] + abs(values[i]) / 2
        sign = "+" if values[i] >= 0 else ""
        label = f"{sign}{values[i]:.1f}U" if i < len(names) - 1 else f"{values[i]:.1f}U"
        ax.text(x[i], mid, label, ha="center", va="center",
                fontsize=11, fontweight="bold", color="white")

    # Connector lines between waterfall bars
    for i in range(len(names) - 2):
        y_connect = bottoms[i] + (values[i] if values[i] >= 0 else 0)
        ax.plot([x[i] + 0.3, x[i + 1] - 0.3], [y_connect, y_connect],
                color="grey", linewidth=0.8, linestyle="--")

    ax.set_xticks(x)
    ax.set_xticklabels(names, fontsize=9)
    ax.axhline(0, color="grey", linewidth=0.5)
    ax.set_ylabel("Unidades de insulina (U)")
    ax.set_title("Desglose de la recomendacion prandial (cascada)")
    y_max = max(bottoms[i] + abs(values[i]) for i in range(len(values))) + 1
    ax.set_ylim(-2, y_max)

    formula_text = "Bolo = Carbs/ICR + (BG$-$Target)/ISF $-$ IOB + Tendencia"
    ax.text(0.5, -0.15, formula_text, transform=ax.transAxes, ha="center",
            fontsize=11, style="italic", color=COLORS["grey"])

    fig.savefig(os.path.join(OUTPUT_DIR, "10_prandial_components.png"))
    plt.close(fig)
    print(" [10/10] Prandial components")


# ─── Main ────────────────────────────────────────────────────────────────────

def main():
    setup_style()
    print(f"Generating TFG graphs to {OUTPUT_DIR}/\n")

    plot_iob_curves()
    plot_insulin_activity()
    plot_iob_stacking()
    plot_basal_iob()
    plot_cob_absorption()
    plot_glucose_forecast()
    plot_bayesian_update()
    plot_mixture_rule()
    plot_confidence_system()
    plot_prandial_components()

    print(f"\nDone! 10 figures saved to {OUTPUT_DIR}/")


if __name__ == "__main__":
    main()
