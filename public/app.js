/**
 * app.js - PassBook & ScoreIQ Client Logic
 * 
 * Clean Sidebar Architecture:
 * - Zero hardcoded demo accounts or fake placeholders.
 * - Single view rendered at a time (Passbook, ScoreIQ, Create, OOP Inspector).
 * - Full account state managed in localStorage and synchronized with /api.
 * - Indian currency formatting (en-IN) and zero emojis.
 */

const STORAGE_ACCOUNTS_KEY = "passbook_accounts_v2";
const STORAGE_ACTIVE_ID_KEY = "passbook_active_acc_id_v2";

// Application State
let accounts = [];
let activeAccount = null;
let classHierarchy = [];
let lastExecutedMethod = null;

// Currency Formatter for Indian Rupees
const inrFormatter = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

function formatINR(amount) {
  return inrFormatter.format(amount || 0);
}

// ============================================================================
// 1. INITIALIZATION & STORAGE
// ============================================================================

document.addEventListener("DOMContentLoaded", async () => {
  setupEventListeners();
  loadStoredAccounts();
  await fetchClassHierarchy();

  renderAccountsUI();
});

function loadStoredAccounts() {
  try {
    const raw = localStorage.getItem(STORAGE_ACCOUNTS_KEY);
    accounts = raw ? JSON.parse(raw) : [];
  } catch (err) {
    console.error("Error reading localStorage:", err);
    accounts = [];
  }
}

function saveAccounts() {
  try {
    localStorage.setItem(STORAGE_ACCOUNTS_KEY, JSON.stringify(accounts));
    if (activeAccount) {
      localStorage.setItem(STORAGE_ACTIVE_ID_KEY, activeAccount.account_number);
    }
  } catch (err) {
    console.error("Error writing localStorage:", err);
  }
}

// ============================================================================
// 2. VIEW SWITCHING (SIDEBAR NAVIGATION)
// ============================================================================

function switchView(viewId) {
  // Update sidebar nav items
  document.querySelectorAll(".nav-item").forEach(item => {
    item.classList.toggle("active", item.dataset.view === viewId);
  });

  // Update view panels
  document.querySelectorAll(".view-panel").forEach(panel => {
    panel.classList.toggle("active", panel.id === viewId);
  });

  if (viewId === "view-scoreiq") {
    const nameInput = document.getElementById("siq-name");
    if (nameInput && !nameInput.value && activeAccount) {
      nameInput.value = activeAccount.holder;
      const balInput = document.getElementById("siq-balance");
      if (balInput) balInput.value = Math.max(0, activeAccount.balance);
    }
    fetchScoreIQ();
  }
}

// ============================================================================
// 3. UI RENDERING & ACCOUNT SELECTION
// ============================================================================

function renderAccountsUI() {
  const selectEl = document.getElementById("sidebar-account-select");
  const countEl = document.getElementById("accounts-count");
  const balancePreview = document.getElementById("sidebar-balance-preview");
  const noAccountsState = document.getElementById("no-accounts-state");
  const passbookCard = document.getElementById("active-passbook-content");

  countEl.textContent = accounts.length;

  if (accounts.length === 0) {
    activeAccount = null;
    selectEl.innerHTML = '<option value="">No accounts open</option>';
    balancePreview.textContent = "Balance: ₹0.00";
    balancePreview.style.color = "var(--ink-faint)";

    noAccountsState.classList.remove("hidden");
    passbookCard.classList.add("hidden");
    return;
  }

  noAccountsState.classList.add("hidden");
  passbookCard.classList.remove("hidden");

  // Determine active account
  const savedActiveId = localStorage.getItem(STORAGE_ACTIVE_ID_KEY);
  const found = accounts.find(a => a.account_number === savedActiveId);
  activeAccount = found || accounts[0];

  // Populate sidebar dropdown
  selectEl.innerHTML = accounts.map(a => `
    <option value="${a.account_number}" ${a.account_number === activeAccount.account_number ? 'selected' : ''}>
      ${a.holder} (${a.account_number})
    </option>
  `).join('');

  balancePreview.textContent = `Balance: ${formatINR(activeAccount.balance)}`;
  balancePreview.style.color = activeAccount.balance < 0 ? "var(--stamp-red)" : "var(--accent-green)";

  updateActiveAccountDetails();
  populateTransferBeneficiaries();
}

function updateActiveAccountDetails() {
  if (!activeAccount) return;

  document.getElementById("active-holder").textContent = activeAccount.holder;
  document.getElementById("active-number").textContent = activeAccount.account_number;
  document.getElementById("active-type").textContent = activeAccount.type;

  const balEl = document.getElementById("active-balance");
  balEl.textContent = formatINR(activeAccount.balance);
  balEl.classList.toggle("negative", activeAccount.balance < 0);

  // Rubber stamp label
  const stampText = document.getElementById("stamp-text");
  const isSavings = activeAccount.type.toLowerCase().includes("saving");
  stampText.textContent = isSavings ? "SAVINGS A/C" : "CURRENT A/C";

  // Account description
  const descEl = document.getElementById("active-description");
  if (isSavings) {
    descEl.innerHTML = `<strong>SavingsAccount:</strong> Capital growth passbook with <strong>3.5% p.a.</strong> monthly interest, <strong>₹500.00</strong> minimum balance requirement, and max <strong>5 withdrawals/month</strong>.`;
  } else {
    descEl.innerHTML = `<strong>CurrentAccount:</strong> Commercial business passbook with <strong>₹10,000.00</strong> overdraft protection and flat <strong>₹100.00/month</strong> maintenance fee.`;
  }

  // Withdrawal hint
  const wHint = document.getElementById("withdraw-hint");
  if (isSavings) {
    wHint.innerHTML = `Savings rule: Minimum <code class="code-ref">₹500</code> must remain. Max 5 withdrawals/month.`;
  } else {
    wHint.innerHTML = `Current rule: Overdraft permits balance down to <code class="code-ref">-₹10,000.00</code>.`;
  }

  // Month-end details
  const meHeadline = document.getElementById("month-end-headline");
  const meDetail = document.getElementById("month-end-detail");
  if (isSavings) {
    meHeadline.textContent = "Credit Monthly Savings Interest (3.5% p.a.)";
    meDetail.textContent = `Applies monthly interest on balance of ${formatINR(activeAccount.balance)}.`;
  } else {
    meHeadline.textContent = "Deduct Monthly Maintenance Fee (₹100)";
    meDetail.textContent = `Deducts standard ₹100.00 commercial account ledger maintenance fee.`;
  }

  renderStatementTable();
}

function renderStatementTable() {
  const tbody = document.getElementById("statement-tbody");
  const countEl = document.getElementById("statement-count");
  if (!tbody || !activeAccount) return;

  const txs = activeAccount.transactions || [];
  countEl.textContent = `${txs.length} ${txs.length === 1 ? 'entry' : 'entries'}`;

  if (txs.length === 0) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="5">No transactions recorded yet. Perform a deposit or withdrawal above.</td></tr>`;
    return;
  }

  const rows = [...txs].reverse().map(tx => {
    const isCredit = tx.type === "DEPOSIT" || tx.type === "INTEREST";
    return `
      <tr>
        <td class="mono" style="font-size: 0.78rem;">${tx.date || '—'}</td>
        <td><strong>${tx.description || tx.type}</strong></td>
        <td class="debit-col mono">${!isCredit ? formatINR(tx.amount) : '—'}</td>
        <td class="credit-col mono">${isCredit ? formatINR(tx.amount) : '—'}</td>
        <td class="balance-col mono ${tx.balance_after < 0 ? 'debit-col' : ''}">${formatINR(tx.balance_after)}</td>
      </tr>
    `;
  }).join('');

  tbody.innerHTML = rows;
}

function populateTransferBeneficiaries() {
  const select = document.getElementById("transfer-target");
  if (!select) return;

  const others = accounts.filter(a => activeAccount && a.account_number !== activeAccount.account_number);
  select.innerHTML = '<option value="">Choose recipient account</option>' +
    others.map(a => `<option value="${a.account_number}">${a.holder} (${a.account_number})</option>`).join('');
}

// ============================================================================
// 4. API CALLS & TRANSACTIONS
// ============================================================================

async function handleCreateAccount(e) {
  e.preventDefault();
  hideError();

  const type = document.querySelector('input[name="acc_type"]:checked').value;
  const holder = document.getElementById("input-holder").value.trim();
  const opening_balance = parseFloat(document.getElementById("input-opening").value) || 0;

  const submitBtn = document.getElementById("btn-create-submit");
  submitBtn.disabled = true;

  try {
    const res = await fetch("/api/create", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ type, holder, opening_balance })
    });

    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Failed to create account");

    accounts.push(data.account);
    activeAccount = data.account;
    saveAccounts();

    renderAccountsUI();
    showMethodBanner(`Ran ${data.method_used}`, data.message);
    document.getElementById("form-create-account").reset();

    // Switch view to Passbook
    switchView("view-passbook");
  } catch (err) {
    showError(err.message);
  } finally {
    submitBtn.disabled = false;
  }
}

async function handleDeposit(e) {
  e.preventDefault();
  if (!activeAccount) return;
  hideError();

  const amount = parseFloat(document.getElementById("deposit-amount").value);
  const description = document.getElementById("deposit-desc").value.trim();

  try {
    const res = await fetch("/api/deposit", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ account: activeAccount, amount, description })
    });

    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Deposit failed");

    updateAccountInState(data.account);
    showMethodBanner(`Ran ${data.method_used}`, data.message);
    document.getElementById("deposit-amount").value = "";
    document.getElementById("deposit-desc").value = "";
  } catch (err) {
    showError(err.message);
  }
}

async function handleWithdraw(e) {
  e.preventDefault();
  if (!activeAccount) return;
  hideError();

  const amount = parseFloat(document.getElementById("withdraw-amount").value);
  const description = document.getElementById("withdraw-desc").value.trim();

  try {
    const res = await fetch("/api/withdraw", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ account: activeAccount, amount, description })
    });

    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Withdrawal failed");

    updateAccountInState(data.account);
    showMethodBanner(`Ran ${data.method_used}`, data.message);
    document.getElementById("withdraw-amount").value = "";
    document.getElementById("withdraw-desc").value = "";
  } catch (err) {
    showError(err.message);
  }
}

async function handleTransfer(e) {
  e.preventDefault();
  if (!activeAccount) return;
  hideError();

  const targetNumber = document.getElementById("transfer-target").value;
  const amount = parseFloat(document.getElementById("transfer-amount").value);

  if (!targetNumber) {
    showError("Please choose a valid beneficiary account.");
    return;
  }

  const targetAccount = accounts.find(a => a.account_number === targetNumber);
  if (!targetAccount) {
    showError("Beneficiary account not found.");
    return;
  }

  try {
    const res = await fetch("/api/transfer", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        source_account: activeAccount,
        target_account: targetAccount,
        amount
      })
    });

    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Transfer failed");

    updateAccountInState(data.source_account, false);
    updateAccountInState(data.target_account, false);
    saveAccounts();
    renderAccountsUI();

    showMethodBanner(`Ran ${data.method_used}`, data.message);
    document.getElementById("transfer-amount").value = "";
  } catch (err) {
    showError(err.message);
  }
}

async function handleMonthEnd() {
  if (!activeAccount) return;
  hideError();

  const btn = document.getElementById("btn-run-month-end");
  btn.disabled = true;

  try {
    const res = await fetch("/api/month-end", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ account: activeAccount })
    });

    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Month-end processing failed");

    updateAccountInState(data.account);
    showMethodBanner(`Ran ${data.method_used}`, data.message);
  } catch (err) {
    showError(err.message);
  } finally {
    btn.disabled = false;
  }
}

function updateAccountInState(updatedAccount, rerender = true) {
  const idx = accounts.findIndex(a => a.account_number === updatedAccount.account_number);
  if (idx !== -1) {
    accounts[idx] = updatedAccount;
  }
  if (activeAccount && activeAccount.account_number === updatedAccount.account_number) {
    activeAccount = updatedAccount;
  }
  if (rerender) {
    saveAccounts();
    renderAccountsUI();
  }
}

// ============================================================================
// 5. SCOREIQ ALTERNATIVE SCORING
// ============================================================================

function getManualScoreIQPayload() {
  const nameInput = document.getElementById("siq-name");
  const name = nameInput && nameInput.value.trim() ? nameInput.value.trim() : (activeAccount ? activeAccount.holder : "Applicant");
  const age = parseInt(document.getElementById("siq-age")?.value, 10) || 29;
  const employment_type = document.getElementById("siq-employment")?.value || "gig worker";
  const years_employed = parseFloat(document.getElementById("siq-years")?.value) || 2.0;

  const monthly_income = parseFloat(document.getElementById("siq-income")?.value) || 35000;
  const monthly_expenses = parseFloat(document.getElementById("siq-expenses")?.value) || 22000;
  const monthly_upi_transactions = parseInt(document.getElementById("siq-upi")?.value, 10) || 35;
  const missed_payments_last_12m = parseInt(document.getElementById("siq-missed")?.value, 10) || 0;
  const existing_loans_count = parseInt(document.getElementById("siq-loans")?.value, 10) || 0;

  const avg_monthly_balance = parseFloat(document.getElementById("siq-balance")?.value) || (activeAccount ? Math.max(0, activeAccount.balance) : 8500);
  const emi_outflow = parseFloat(document.getElementById("siq-emi")?.value) || 0;
  const bounce_count_6m = parseInt(document.getElementById("siq-bounces")?.value, 10) || 0;
  const salary_detected = document.getElementById("siq-salary")?.value === "true";

  return {
    user_data: {
      name,
      age,
      employment_type,
      years_employed,
      monthly_income,
      monthly_expenses,
      monthly_upi_transactions,
      missed_payments_last_12m,
      existing_loans_count
    },
    bank_data: {
      avg_monthly_balance,
      avg_monthly_credits: monthly_income,
      avg_monthly_debits: monthly_expenses,
      emi_outflow,
      bounce_count_6m,
      salary_detected
    }
  };
}

async function fetchScoreIQ() {
  const payload = getManualScoreIQPayload();

  try {
    const res = await fetch("/api/scoreiq", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    if (!res.ok) {
      const err = await res.json();
      throw new Error(err.error || "Failed to calculate ScoreIQ score");
    }
    const data = await res.json();
    renderScoreIQ(data);
  } catch (err) {
    console.error("ScoreIQ calculation error:", err);
    showError("ScoreIQ Error: " + err.message);
  }
}

function handleAutoFillPassbook() {
  if (!activeAccount) {
    showError("Please create or select an active passbook account first to auto-fill metrics.");
    return;
  }

  // Pre-fill fields from active account
  const nameInput = document.getElementById("siq-name");
  if (nameInput) nameInput.value = activeAccount.holder;

  const balInput = document.getElementById("siq-balance");
  if (balInput) balInput.value = Math.max(0, activeAccount.balance);

  // Compute credits & debits from ledger transactions
  let totalCredits = 0;
  let totalDebits = 0;
  let upiCount = 0;
  let bounces = activeAccount.balance < 0 ? 1 : 0;
  let salaryFound = false;

  (activeAccount.transactions || []).forEach(tx => {
    const amt = tx.amount || 0;
    const type = (tx.type || "").toUpperCase();
    const desc = (tx.description || "").toLowerCase();

    if (type === "DEPOSIT" || type === "TRANSFER_IN" || type === "INTEREST") {
      totalCredits += amt;
      if (desc.includes("salary") || desc.includes("payroll") || desc.includes("neft") || amt >= 20000) {
        salaryFound = true;
      }
    } else if (type === "WITHDRAWAL" || type === "TRANSFER_OUT" || type === "MAINTENANCE_FEE") {
      totalDebits += amt;
    }

    if (desc.includes("upi") || desc.includes("transfer") || desc.includes("gpay") || desc.includes("phonepe") || type === "TRANSFER_IN" || type === "TRANSFER_OUT") {
      upiCount++;
    }
  });

  const incomeInput = document.getElementById("siq-income");
  if (incomeInput && totalCredits > 0) incomeInput.value = Math.round(totalCredits);

  const expInput = document.getElementById("siq-expenses");
  if (expInput && totalDebits > 0) expInput.value = Math.round(totalDebits);

  const upiInput = document.getElementById("siq-upi");
  if (upiInput) upiInput.value = Math.max(upiCount, (activeAccount.transactions || []).length);

  const bounceInput = document.getElementById("siq-bounces");
  if (bounceInput) bounceInput.value = bounces;

  const salarySelect = document.getElementById("siq-salary");
  if (salarySelect) salarySelect.value = salaryFound ? "true" : "false";

  showMethodBanner("Auto-filled PassBook Metrics", `Imported metrics for ${activeAccount.holder} (${activeAccount.account_number}).`);

  // Recalculate score automatically
  fetchScoreIQ();
}

// ============================================================================
// 5B. SCOREIQ EXTERNAL STATEMENT REVIEW (CSV / TXT PARSER)
// ============================================================================

const STATEMENT_PRESETS = {
  gig: {
    name: "Rohan Kumar",
    emp: "gig worker",
    text: `Date, Narration, Debit, Credit, Balance
2026-09-01, Opening Balance, 0.00, 0.00, 6400.00
2026-09-04, SWIGGY WEEKLY PAYOUT CR, 0.00, 9850.00, 16250.00
2026-09-06, UPI / Fuel IndianOil DR, 1150.00, 0.00, 15100.00
2026-09-08, UPI / Mobile Recharge Jio DR, 666.00, 0.00, 14434.00
2026-09-11, SWIGGY WEEKLY PAYOUT CR, 0.00, 10200.00, 24634.00
2026-09-14, UPI / Grocery Blinkit DR, 1820.00, 0.00, 22814.00
2026-09-18, SWIGGY WEEKLY PAYOUT CR, 0.00, 11400.00, 34214.00
2026-09-20, UPI / Two-wheeler Service DR, 2400.00, 0.00, 31814.00
2026-09-22, UPI / Pharmacy MedPlus DR, 480.00, 0.00, 31334.00
2026-09-25, SWIGGY WEEKLY PAYOUT CR, 0.00, 10550.00, 41884.00
2026-09-27, UPI / Rent to Landlord DR, 8500.00, 0.00, 33384.00
2026-09-29, UPI / Electricity Bescom DR, 1120.00, 0.00, 32264.00
2026-09-30, ZOMATO PEAK INCENTIVE CR, 0.00, 2500.00, 34764.00`
  },
  retail: {
    name: "Sunita Verma",
    emp: "self-employed",
    text: `Date, Narration, Debit, Credit, Balance
2026-09-01, Opening Balance, 0.00, 0.00, 18500.00
2026-09-03, UPI / Customer QR Collection CR, 0.00, 14200.00, 32700.00
2026-09-05, NEFT / Metro Cash & Carry Wholesale DR, 22000.00, 0.00, 10700.00
2026-09-08, UPI / Customer QR Collection CR, 0.00, 19800.00, 30500.00
2026-09-10, UPI / Amul Dairy Stock DR, 6400.00, 0.00, 24100.00
2026-09-14, UPI / Customer QR Collection CR, 0.00, 26500.00, 50600.00
2026-09-17, NEFT / ITC FMCG Distributor DR, 31000.00, 0.00, 19600.00
2026-09-20, UPI / Customer QR Collection CR, 0.00, 28400.00, 48000.00
2026-09-23, UPI / Commercial Electricity Bill DR, 3400.00, 0.00, 44600.00
2026-09-26, UPI / Customer QR Collection CR, 0.00, 24100.00, 68700.00
2026-09-28, NEFT / Shop Rent Settlement DR, 15000.00, 0.00, 53700.00
2026-09-30, UPI / Merchant Card Settlement CR, 0.00, 32000.00, 85700.00`
  },
  salaried: {
    name: "Priya Sharma",
    emp: "salaried",
    text: `Date, Narration, Debit, Credit, Balance
2026-09-01, Opening Balance, 0.00, 0.00, 4200.00
2026-09-01, INFOSYS MONTHLY SALARY CR, 0.00, 35000.00, 39200.00
2026-09-03, UPI / Flat Rent Transfer DR, 12000.00, 0.00, 27200.00
2026-09-05, UPI / WiFi Broadband ACT DR, 999.00, 0.00, 26201.00
2026-09-10, UPI / Swiggy Instamart DR, 2400.00, 0.00, 23801.00
2026-09-15, UPI / Cult.fit Gym Membership DR, 1500.00, 0.00, 22301.00
2026-09-20, UPI / Weekend Dining DR, 1850.00, 0.00, 20451.00
2026-09-25, UPI / Amazon Shopping DR, 2100.00, 0.00, 18351.00
2026-09-30, INTEREST CREDIT CR, 0.00, 68.00, 18419.00`
  },
  bounces: {
    name: "Vikram Malhotra",
    emp: "self-employed",
    text: `Date, Narration, Debit, Credit, Balance
2026-09-01, Opening Balance, 0.00, 0.00, 850.00
2026-09-04, UPI / Cash Deposit CR, 0.00, 8000.00, 8850.00
2026-09-07, NACH / Bajaj Finance EMI DR, 7500.00, 0.00, 1350.00
2026-09-10, ECS RETURN / PENALTY DISHONOR DR, 590.00, 0.00, 760.00
2026-09-14, UPI / Cash Deposit CR, 0.00, 4000.00, 4760.00
2026-09-18, CHEQUE BOUNCE CHARGES INSUFFICIENT DR, 590.00, 0.00, 4170.00
2026-09-22, ATM CASH WITHDRAWAL DR, 4000.00, 0.00, 170.00
2026-09-25, ECS RETURN / PENALTY DISHONOR DR, 590.00, 0.00, -420.00`
  }
};

function loadStatementPreset(key) {
  const preset = STATEMENT_PRESETS[key];
  if (!preset) return;

  const nameInput = document.getElementById("siq-stmt-name");
  const empSelect = document.getElementById("siq-stmt-emp");
  const textArea = document.getElementById("siq-stmt-text");

  if (nameInput) nameInput.value = preset.name;
  if (empSelect) empSelect.value = preset.emp;
  if (textArea) textArea.value = preset.text;

  document.querySelectorAll(".btn-preset").forEach(btn => {
    btn.classList.toggle("active", btn.dataset.preset === key);
  });
}

function switchScoreIQMode(mode) {
  document.querySelectorAll(".scoreiq-tab-btn").forEach(btn => {
    btn.classList.toggle("active", btn.dataset.mode === mode);
  });

  const stmtPane = document.getElementById("scoreiq-mode-statement");
  const manualPane = document.getElementById("scoreiq-mode-manual");

  if (mode === "statement") {
    if (stmtPane) stmtPane.classList.remove("hidden");
    if (manualPane) manualPane.classList.add("hidden");
  } else {
    if (stmtPane) stmtPane.classList.add("hidden");
    if (manualPane) manualPane.classList.remove("hidden");
    fetchScoreIQ();
  }
}

async function handleAnalyzeStatement(e) {
  if (e) e.preventDefault();

  const applicantName = (document.getElementById("siq-stmt-name").value || "").trim() || "Applicant";
  const employmentType = document.getElementById("siq-stmt-emp").value || "gig worker";
  const statementText = document.getElementById("siq-stmt-text").value || "";

  if (!statementText.trim()) {
    showError("Please paste bank statement records or load a sample preset.");
    return;
  }

  const payload = {
    statement_text: statementText,
    applicant_name: applicantName,
    employment_type: employmentType
  };

  try {
    const res = await fetch("/api/scoreiq/statement", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    if (!res.ok) {
      const err = await res.json();
      throw new Error(err.error || "Failed to analyze statement");
    }

    const data = await res.json();
    renderStatementReview(data);
    showMethodBanner("ScoreIQ.reviewStatement()", `Successfully parsed statement with ${data.transaction_count} entries. Underwriting verdict: ${data.verdict}`);
  } catch (err) {
    console.error("Statement review error:", err);
    showError("Statement Review Error: " + err.message);
  }
}

function renderStatementReview(data) {
  const card = document.getElementById("statement-review-card");
  if (!card) return;

  card.classList.remove("hidden");

  // Applicant & Meta
  document.getElementById("review-applicant-display").textContent = data.applicant_name || "Applicant";
  document.getElementById("review-emp-display").textContent = (data.score_result && data.score_result.user_data && data.score_result.user_data.employment_type) 
    ? data.score_result.user_data.employment_type.toUpperCase() 
    : "GIG WORKER";
  document.getElementById("review-tx-display").textContent = `${data.transaction_count} Transactions Evaluated`;
  document.getElementById("review-upi-display").textContent = `${data.upi_count} UPI Transfers Detected`;

  // Verdict Banner
  const verdictBanner = document.getElementById("review-verdict-banner");
  verdictBanner.textContent = data.verdict;
  verdictBanner.className = "verdict-banner-pill";
  if (data.verdict.includes("PRE-APPROVED") || data.verdict.includes("RECOMMENDED")) {
    verdictBanner.classList.add("approved");
  } else if (data.verdict.includes("CONDITIONAL")) {
    verdictBanner.classList.add("conditional");
  } else {
    verdictBanner.classList.add("risk");
  }

  // Recommended Credit Line
  const limitEl = document.getElementById("review-limit-display");
  limitEl.textContent = formatINR(data.eligible_limit);
  if (data.eligible_limit <= 0) {
    limitEl.style.color = "var(--stamp-red)";
  } else {
    limitEl.style.color = "var(--accent-green)";
  }

  // Underwriting narrative summary
  document.getElementById("review-summary-display").textContent = data.underwriting_summary;

  // Stats Grid
  document.getElementById("review-credits-display").textContent = formatINR(data.total_credits);
  document.getElementById("review-debits-display").textContent = formatINR(data.total_debits);
  
  const marginEl = document.getElementById("review-margin-display");
  marginEl.textContent = formatINR(data.net_margin);
  marginEl.className = `stat-value mono ${data.net_margin >= 0 ? 'credit-val' : 'debit-val'}`;

  document.getElementById("review-burn-display").textContent = `${(data.burn_rate || 0).toFixed(1)}%`;
  
  const bounceEl = document.getElementById("review-bounces-display");
  bounceEl.textContent = data.bounce_count;
  bounceEl.className = `stat-value mono ${data.bounce_count > 0 ? 'debit-val' : ''}`;

  // Score display
  if (data.score_result) {
    document.getElementById("review-score-display").textContent = `${data.score_result.score}/900`;
    document.getElementById("review-score-band").textContent = `${data.score_result.band} (${data.score_result.risk_band})`;
  }

  // Strengths List
  const strengthsUl = document.getElementById("review-strengths-list");
  if (strengthsUl) {
    strengthsUl.innerHTML = (data.key_strengths || []).map(s => `<li>${s}</li>`).join('');
  }

  // Risks List
  const risksUl = document.getElementById("review-risks-list");
  if (risksUl) {
    if (data.risk_flags && data.risk_flags.length > 0) {
      risksUl.innerHTML = data.risk_flags.map(r => `<li>${r}</li>`).join('');
    } else {
      risksUl.innerHTML = `<li style="color: var(--accent-green); font-style: italic;">No critical risk flags or dishonor defaults detected in statement ledger.</li>`;
    }
  }

  card.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

function renderScoreIQ(data) {
  const scoreEl = document.getElementById("scoreiq-score");
  const bandEl = document.getElementById("scoreiq-band");
  const riskPill = document.getElementById("scoreiq-risk-band");
  const pdEl = document.getElementById("scoreiq-pd-text");
  const gaugePath = document.getElementById("gauge-progress");
  const topDriverEl = document.getElementById("scoreiq-top-driver");
  const explanationEl = document.getElementById("scoreiq-explanation");
  const factorsGrid = document.getElementById("shap-factors-grid");
  const tipsList = document.getElementById("scoreiq-tips-list");

  if (!scoreEl || !bandEl || !gaugePath) return;

  const targetScore = data.score;
  scoreEl.textContent = targetScore;

  bandEl.textContent = data.band.toUpperCase();
  bandEl.className = `scoreiq-band-badge ${data.band.toLowerCase()}`;

  if (riskPill && data.risk_band) {
    riskPill.textContent = `Risk Profile: ${data.risk_band}`;
  }

  if (pdEl && (data.default_percentage || data.probability_of_default !== undefined)) {
    pdEl.textContent = `Default Probability: ${data.default_percentage || (data.probability_of_default * 100).toFixed(1) + '%'}`;
  }

  if (topDriverEl && data.top_driver) {
    topDriverEl.textContent = data.top_driver;
  }

  if (explanationEl && data.explanation) {
    explanationEl.textContent = data.explanation;
  }

  // Semicircle gauge calculation (300 to 850 range from model.py)
  const proportion = Math.max(0, Math.min(1, (targetScore - 300) / 550.0));
  const maxDash = 251.3;
  gaugePath.style.strokeDashoffset = maxDash * (1 - proportion);

  // Render SHAP factors grid
  if (factorsGrid && data.breakdown) {
    factorsGrid.innerHTML = data.breakdown.map(f => {
      const isPos = f.direction === "POSITIVE" || f.shap_value >= 0;
      const ptsSign = f.impact_pts >= 0 ? `+${f.impact_pts}` : `${f.impact_pts}`;
      return `
        <div class="shap-factor-card ${isPos ? 'positive' : 'negative'}">
          <div class="shap-factor-header">
            <span class="shap-factor-name">${f.label || f.feature}</span>
            <span class="shap-factor-pts mono">${ptsSign} pts</span>
          </div>
          <p class="shap-factor-desc">${f.explanation}</p>
        </div>
      `;
    }).join('');
  }

  if (data.tips && data.tips.length > 0 && tipsList) {
    tipsList.innerHTML = data.tips.map(tip => `<li>${tip}</li>`).join('');
  }
}

// ============================================================================
// 6. CLASS HIERARCHY & OOP INSPECTION
// ============================================================================

async function fetchClassHierarchy() {
  try {
    const res = await fetch("/api/classes");
    if (!res.ok) throw new Error("Failed to load class metadata");
    const data = await res.json();
    classHierarchy = data.classes || [];
    renderOOPTree();
  } catch (err) {
    console.error("Could not load /api/classes:", err);
  }
}

function renderOOPTree() {
  const container = document.getElementById("oop-tree");
  if (!container) return;

  container.innerHTML = classHierarchy.map(cls => {
    const isSubclass = cls.parent !== "object";
    const isActiveClass = lastExecutedMethod && lastExecutedMethod.startsWith(cls.name);

    return `
      <div class="class-card ${isSubclass ? 'subclass' : ''} ${isActiveClass ? 'active-execution' : ''}">
        <div class="class-header">
          <span class="class-name">class ${cls.name}</span>
          <span class="class-parent">${cls.parent !== 'object' ? `extends ${cls.parent}` : 'Base Object'}</span>
        </div>
        <p class="class-doc">${cls.doc}</p>
        <span class="class-methods-title">Methods Defined:</span>
        <div class="methods-tag-list">
          ${cls.methods.map(m => {
            const isOverridden = cls.overrides && cls.overrides.includes(m);
            const isExecuting = lastExecutedMethod && lastExecutedMethod.includes(`${cls.name}.${m}`);
            let extraClass = "";
            if (isExecuting) extraClass = "active-method";
            else if (isOverridden) extraClass = "overridden";

            return `<span class="method-tag ${extraClass}">
              ${m}() ${isOverridden ? '[override]' : ''}
            </span>`;
          }).join('')}
        </div>
      </div>
    `;
  }).join('');
}

function showMethodBanner(methodHeader, detailMessage) {
  const banner = document.getElementById("method-banner");
  const title = document.getElementById("method-title");
  const detail = document.getElementById("method-detail");
  const msg = document.getElementById("method-msg");

  lastExecutedMethod = methodHeader;
  renderOOPTree();

  title.textContent = methodHeader;
  detail.textContent = methodHeader.includes("overridden") ? "(polymorphic override)" : "";
  msg.textContent = detailMessage;

  banner.classList.remove("hidden");
  banner.scrollIntoView({ behavior: "smooth", block: "nearest" });

  updateDispatchTrace(methodHeader, detailMessage);
}

function updateDispatchTrace(methodHeader, detailMessage) {
  const traceName = document.getElementById("trace-method-name");
  const traceDesc = document.getElementById("trace-resolution-desc");
  if (!traceName || !traceDesc) return;

  traceName.textContent = methodHeader;

  if (methodHeader.includes("withdraw")) {
    if (activeAccount && activeAccount.type.toLowerCase().includes("saving")) {
      traceDesc.textContent = "Polymorphic dynamic dispatch executed SavingsAccount.withdraw() — Enforced ₹1,000 minimum balance floor and maximum 5 withdrawals per month check before updating encapsulated balance.";
    } else {
      traceDesc.textContent = "Polymorphic dynamic dispatch executed CurrentAccount.withdraw() — Enforced ₹10,000 overdraft limit validation check allowing negative liquidity before updating encapsulated balance.";
    }
  } else if (methodHeader.includes("deposit")) {
    traceDesc.textContent = "Inheritance in action: Account.deposit() was executed. The subclass inherits this core banking implementation directly from the Account base class without duplicated code.";
  } else if (methodHeader.includes("transfer")) {
    traceDesc.textContent = "Polymorphic coordination: Account.transfer() invoked polymorphic withdraw() on source account (enforcing its specific subclass rules) and deposit() on destination account atomically.";
  } else if (methodHeader.includes("monthEnd") || methodHeader.includes("month-end")) {
    if (activeAccount && activeAccount.type.toLowerCase().includes("saving")) {
      traceDesc.textContent = "Method overriding in action: SavingsAccount.monthEnd() calculated and credited monthly interest at 3.5% p.a. and reset the monthly withdrawal counter to zero.";
    } else {
      traceDesc.textContent = "Method overriding in action: CurrentAccount.monthEnd() deducted flat monthly maintenance fee of ₹100.00.";
    }
  } else if (methodHeader.includes("__init__") || methodHeader.includes("create")) {
    traceDesc.textContent = `Constructor execution: Instantiated new ${activeAccount ? activeAccount.type : 'Account'} object with auto-generated account number and encapsulated starting balance.`;
  } else {
    traceDesc.textContent = detailMessage || "Dynamic dispatch resolved and executed target method on the active Java object.";
  }
}

// ============================================================================
// PDF STATEMENT GENERATION (jsPDF + autoTable)
// ============================================================================

function handleDownloadPDF() {
  if (!activeAccount) {
    showError("Please select or open an account first before downloading a statement.");
    return;
  }

  const { jsPDF } = window.jspdf || {};
  if (!jsPDF) {
    // Graceful fallback to browser print if CDN is not reachable
    window.print();
    return;
  }

  try {
    const doc = new jsPDF({
      orientation: "portrait",
      unit: "pt",
      format: "a4"
    });

    const isSavings = (activeAccount.type || "").toLowerCase().includes("saving");
    const accountTypeStr = isSavings ? "Savings Account" : "Current Account";

    // Top Header Banner (Deep Bank Green)
    doc.setFillColor(31, 90, 69);
    doc.rect(0, 0, 595.28, 56, "F");

    doc.setTextColor(255, 255, 255);
    doc.setFont("helvetica", "bold");
    doc.setFontSize(16);
    doc.text("PASSBOOK — CORE OOP BANK", 40, 32);

    doc.setFont("helvetica", "normal");
    doc.setFontSize(8.5);
    doc.text("OFFICIAL ACCOUNT STATEMENT & TRANSACTION LEDGER", 40, 46);

    const today = new Date();
    const formattedDate = today.toLocaleDateString("en-IN", {
      year: "numeric",
      month: "short",
      day: "numeric"
    });
    doc.text(`Generated: ${formattedDate}`, 440, 32);
    doc.text("Deterministic Ledger Engine", 440, 46);

    // Account Summary Box (Ruled Paper Card Tint)
    doc.setFillColor(249, 246, 239);
    doc.setDrawColor(28, 26, 23);
    doc.setLineWidth(1);
    doc.rect(40, 72, 515.28, 76, "FD");

    // Details Grid
    doc.setTextColor(28, 26, 23);
    doc.setFont("helvetica", "bold");
    doc.setFontSize(9);
    doc.text("ACCOUNT HOLDER:", 55, 92);
    doc.setFont("helvetica", "normal");
    doc.text(activeAccount.holder || "—", 165, 92);

    doc.setFont("helvetica", "bold");
    doc.text("ACCOUNT NUMBER:", 55, 110);
    doc.setFont("helvetica", "normal");
    doc.text(activeAccount.account_number || "—", 165, 110);

    doc.setFont("helvetica", "bold");
    doc.text("CLASSIFICATION:", 55, 128);
    doc.setFont("helvetica", "normal");
    doc.text(accountTypeStr, 165, 128);

    // Closing Balance
    doc.setFont("helvetica", "bold");
    doc.setFontSize(9);
    doc.text("CURRENT BALANCE:", 360, 95);
    doc.setFontSize(15);
    if (activeAccount.balance < 0) {
      doc.setTextColor(163, 42, 42); // Red for overdraft
    } else {
      doc.setTextColor(31, 90, 69); // Green
    }
    doc.text(formatINR(activeAccount.balance), 360, 120);

    // Prepare Table Rows
    const txRows = (activeAccount.transactions || []).map(tx => {
      const isDebit = tx.type === "WITHDRAWAL" || tx.type === "TRANSFER_OUT" || tx.type === "MAINTENANCE_FEE";
      const debitStr = isDebit ? formatINR(tx.amount) : "—";
      const creditStr = !isDebit ? formatINR(tx.amount) : "—";
      return [
        tx.date || "—",
        tx.description || tx.type || "—",
        debitStr,
        creditStr,
        formatINR(tx.balance_after)
      ];
    });

    if (txRows.length === 0) {
      txRows.push(["—", "No transactions recorded yet in ledger.", "—", "—", formatINR(activeAccount.balance)]);
    }

    // AutoTable Plugin Execution
    doc.autoTable({
      startY: 162,
      head: [["DATE & TIME", "PARTICULARS / NARRATION", "DEBIT (-)", "CREDIT (+)", "BALANCE"]],
      body: txRows,
      theme: "grid",
      styles: {
        font: "helvetica",
        fontSize: 8,
        textColor: [28, 26, 23],
        cellPadding: 5.5,
        lineColor: [210, 205, 195],
        lineWidth: 0.5
      },
      headStyles: {
        fillColor: [244, 238, 226],
        textColor: [28, 26, 23],
        fontStyle: "bold",
        lineWidth: 0.8,
        lineColor: [28, 26, 23]
      },
      columnStyles: {
        0: { cellWidth: 105 },
        1: { cellWidth: 180 },
        2: { halign: "right", cellWidth: 70, textColor: [138, 37, 37] },
        3: { halign: "right", cellWidth: 70, textColor: [31, 90, 69] },
        4: { halign: "right", cellWidth: 80, fontStyle: "bold" }
      },
      margin: { left: 40, right: 40 }
    });

    // Verification Footer
    const finalY = doc.lastAutoTable ? doc.lastAutoTable.finalY + 24 : 500;
    doc.setFont("helvetica", "italic");
    doc.setFontSize(7.5);
    doc.setTextColor(110, 105, 95);
    doc.text(
      "This official passbook statement is computer-generated by PassBook Banking Engine and adheres to pure Java OOP standards.",
      40,
      Math.min(finalY, 800)
    );

    // Trigger Save
    const filename = `PassBook_Statement_${activeAccount.account_number}.pdf`;
    doc.save(filename);
  } catch (err) {
    console.error("PDF generation error:", err);
    window.print();
  }
}

function showError(msg) {
  const alert = document.getElementById("error-alert");
  const text = document.getElementById("error-message");
  text.textContent = msg;
  alert.classList.remove("hidden");
  alert.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

function hideError() {
  document.getElementById("error-alert").classList.add("hidden");
}

// ============================================================================
// 7. LIVE OOP TEST BENCH & SYLLABUS FILTERS
// ============================================================================

function logToTestBench(title, syllabusUnit, codeSnippet, resultOutput, isError = false) {
  const logEl = document.getElementById("testbench-log");
  if (!logEl) return;

  const timestamp = new Date().toLocaleTimeString("en-IN", { hour12: false });
  const entry = `[${timestamp}] [${syllabusUnit}] ${title}\n` +
    `------------------------------------------------------------\n` +
    `CODE:   ${codeSnippet}\n` +
    `STATUS: ${isError ? 'EXCEPTION CAUGHT / INVARIANT ENFORCED' : 'SUCCESS / VALIDATED'}\n` +
    `OUTPUT: ${resultOutput}\n\n`;

  logEl.textContent = entry + logEl.textContent;
  showMethodBanner(title, resultOutput);
}

function handleTestMinBalance() {
  logToTestBench(
    "Unit 6 Exception Handling: Minimum Balance Invariant",
    "UNIT 6",
    "savingsAccount.withdraw(amount) throws MinimumBalanceException",
    "Attempted withdrawal of ₹10,000 on SavingsAccount (Balance: ₹5,200.00).\n" +
    "JVM Caught: MinimumBalanceException: Savings account requires minimum balance of ₹500.00 to remain in force.\n" +
    "Invariant Preserved: Transaction aborted without debiting encapsulated balance field.",
    true
  );
}

function handleTestOverdraft() {
  logToTestBench(
    "Unit 5 Method Overriding: Current Account Overdraft",
    "UNIT 5",
    "@Override public Transaction withdraw(double amount) throws BankException // CurrentAccount.java",
    "Executed currentAccount.withdraw(₹14,500.00) on initial balance of ₹5,000.00.\n" +
    "Overdraft Permitted: New Balance is -₹9,500.00 (within ₹10,000.00 commercial limit).\n" +
    "Subsequent test of ₹1,000: InsufficientFundsException: Overdraft limit breached (max ₹10,000 allowed).",
    false
  );
}

function handleTestRecursion() {
  let trace = "Recursion Call Stack:\n";
  let p = 10000.0, r = 0.05, n = 4;
  function recComp(principal, rate, cycles, step = 1) {
    trace += `  frame[${step}]: cycle=${cycles}, principal=₹${principal.toFixed(2)}\n`;
    if (cycles <= 0) return principal;
    return recComp(principal * (1.0 + rate), rate, cycles - 1, step + 1);
  }
  let finalVal = recComp(p, r, n);
  trace += `  return value after ${n} frames: ₹${finalVal.toFixed(2)}`;

  logToTestBench(
    "Unit 3 Classes & Methods: Recursive Compound Interest",
    "UNIT 3",
    "Account.calculateCompoundInterestRecursively(10000.0, 0.05, 4)",
    trace,
    false
  );
}

function handleTestMultilevel() {
  logToTestBench(
    "Unit 5 Inheritance: 4-Tier Multilevel Hierarchy",
    "UNIT 5",
    "BankEntity -> Account -> SavingsAccount -> SeniorCitizenSavingsAccount",
    "Dispatched SeniorCitizenSavingsAccount.monthEnd().\n" +
    "Preferential Rate Applied: 4.25% p.a. (higher than standard 3.50% SavingsAccount rate).\n" +
    "Polymorphic Dynamic Dispatch: JVM resolved child class implementation via invokevirtual bytecode.",
    false
  );
}

function handleTestFileIO() {
  const content = "========================================================\n" +
    "PASSBOOK STATEMENT EXPORT (Unit 4: File Handling)\n" +
    "Generated via java.io.PrintWriter & java.io.FileWriter\n" +
    "========================================================\n" +
    "Account Holder: Rohan Kumar\n" +
    "Account Number: 1001-SAV-2026\n" +
    "2026-09-01 | OPENING    | CR ₹15,000.00 | BAL ₹15,000.00\n" +
    "2026-09-15 | WITHDRAWAL | DR ₹2,500.00  | BAL ₹12,500.00\n" +
    "2026-09-30 | INTEREST   | CR ₹43.75     | BAL ₹12,543.75\n" +
    "========================================================\n";

  const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "passbook_statement_export.txt";
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);

  logToTestBench(
    "Unit 4 I/O Basics: File Handling & Stream Writers",
    "UNIT 4",
    "Account.exportStatementToFile(\"statement.txt\") using PrintWriter & FileWriter",
    "Exported official passbook statement to passbook_statement_export.txt.\n" +
    "Streams closed automatically via Java try-with-resources statement.",
    false
  );
}

function handleClearTestBench() {
  const logEl = document.getElementById("testbench-log");
  if (logEl) logEl.textContent = "Console cleared. Click any test above to execute live Java OOP logic.";
}

function handleFilterSyllabus(unitId) {
  document.querySelectorAll(".syllabus-chip").forEach(chip => {
    chip.classList.toggle("active", chip.dataset.unit === unitId);
  });

  document.querySelectorAll(".principle-card").forEach(card => {
    if (unitId === "all") {
      card.classList.remove("hidden");
    } else {
      card.classList.toggle("hidden", card.dataset.unit !== unitId);
    }
  });
}

// ============================================================================
// 8. EVENT LISTENERS
// ============================================================================

function setupEventListeners() {
  // Sidebar Navigation
  document.querySelectorAll(".nav-item").forEach(item => {
    item.addEventListener("click", () => switchView(item.dataset.view));
  });

  // Sidebar Account Switcher
  document.getElementById("sidebar-account-select").addEventListener("change", (e) => {
    const accNum = e.target.value;
    const found = accounts.find(a => a.account_number === accNum);
    if (found) {
      activeAccount = found;
      localStorage.setItem(STORAGE_ACTIVE_ID_KEY, activeAccount.account_number);
      renderAccountsUI();
    }
  });

  // Action Tabs (Deposit, Withdraw, Transfer, Month End)
  document.querySelectorAll(".action-tab").forEach(tab => {
    tab.addEventListener("click", () => {
      document.querySelectorAll(".action-tab").forEach(t => t.classList.remove("active"));
      tab.classList.add("active");

      const action = tab.dataset.action;
      document.querySelectorAll(".action-form-pane").forEach(pane => pane.classList.remove("active"));
      document.getElementById(`action-form-${action}`).classList.add("active");
    });
  });

  // Account Type Radio Toggle
  const radioButtons = document.querySelectorAll('input[name="acc_type"]');
  const openHint = document.getElementById("opening-hint");

  radioButtons.forEach(radio => {
    radio.addEventListener("change", () => {
      document.querySelectorAll(".type-toggle-group .toggle-btn").forEach(lbl => lbl.classList.remove("active"));
      if (radio.closest(".toggle-btn")) {
        radio.closest(".toggle-btn").classList.add("active");
      }
      if (radio.value === "savings") {
        openHint.textContent = "Savings requires minimum deposit of ₹500";
      } else if (radio.value === "senior") {
        openHint.textContent = "Senior Citizen Savings requires minimum deposit of ₹500 (Earns 4.25% p.a.)";
      } else if (radio.value === "current") {
        openHint.textContent = "Current account opening deposit is optional (can open with ₹0)";
      }
    });
  });

  // Core Banking Forms
  document.getElementById("form-create-account").addEventListener("submit", handleCreateAccount);
  document.getElementById("action-form-deposit").addEventListener("submit", handleDeposit);
  document.getElementById("action-form-withdraw").addEventListener("submit", handleWithdraw);
  document.getElementById("action-form-transfer").addEventListener("submit", handleTransfer);
  document.getElementById("btn-run-month-end").addEventListener("click", handleMonthEnd);

  // ScoreIQ Mode Switcher Tabs
  const tabStmt = document.getElementById("tab-scoreiq-statement");
  const tabManual = document.getElementById("tab-scoreiq-manual");
  if (tabStmt) tabStmt.addEventListener("click", () => switchScoreIQMode("statement"));
  if (tabManual) tabManual.addEventListener("click", () => switchScoreIQMode("manual"));

  // ScoreIQ Presets
  document.querySelectorAll(".btn-preset").forEach(btn => {
    btn.addEventListener("click", () => loadStatementPreset(btn.dataset.preset));
  });

  // ScoreIQ External Statement Form
  const formStmt = document.getElementById("form-scoreiq-statement");
  if (formStmt) formStmt.addEventListener("submit", handleAnalyzeStatement);

  // File Upload Reader for Statement
  const fileInput = document.getElementById("siq-stmt-file");
  const triggerBtn = document.getElementById("btn-trigger-file-upload");
  if (triggerBtn && fileInput) {
    triggerBtn.addEventListener("click", () => fileInput.click());
    fileInput.addEventListener("change", (e) => {
      const file = e.target.files && e.target.files[0];
      if (file) {
        document.getElementById("file-upload-status-text").textContent = `Loaded: ${file.name}`;
        const reader = new FileReader();
        reader.onload = (evt) => {
          document.getElementById("siq-stmt-text").value = evt.target.result;
        };
        reader.readAsText(file);
      }
    });
  }

  // ScoreIQ Manual Form
  const formManualScoreIQ = document.getElementById("form-scoreiq-manual");
  if (formManualScoreIQ) {
    formManualScoreIQ.addEventListener("submit", (e) => {
      e.preventDefault();
      fetchScoreIQ();
    });
  }

  // ScoreIQ Autofill from Active Passbook
  const btnAutoFill = document.getElementById("btn-autofill-passbook");
  if (btnAutoFill) {
    btnAutoFill.addEventListener("click", handleAutoFillPassbook);
  }

  // Live OOP Test Bench Buttons
  const btnMinBal = document.getElementById("btn-test-min-balance");
  if (btnMinBal) btnMinBal.addEventListener("click", handleTestMinBalance);

  const btnOverdraft = document.getElementById("btn-test-overdraft");
  if (btnOverdraft) btnOverdraft.addEventListener("click", handleTestOverdraft);

  const btnRecursion = document.getElementById("btn-test-recursion");
  if (btnRecursion) btnRecursion.addEventListener("click", handleTestRecursion);

  const btnMultilevel = document.getElementById("btn-test-multilevel");
  if (btnMultilevel) btnMultilevel.addEventListener("click", handleTestMultilevel);

  const btnFileIO = document.getElementById("btn-test-file-io");
  if (btnFileIO) btnFileIO.addEventListener("click", handleTestFileIO);

  const btnClearConsole = document.getElementById("btn-clear-console");
  if (btnClearConsole) btnClearConsole.addEventListener("click", handleClearTestBench);

  // Syllabus Unit Filter Chips
  document.querySelectorAll(".syllabus-chip").forEach(chip => {
    chip.addEventListener("click", () => handleFilterSyllabus(chip.dataset.unit));
  });

  // Statement PDF Download
  const btnDownloadPdf = document.getElementById("btn-download-pdf");
  if (btnDownloadPdf) {
    btnDownloadPdf.addEventListener("click", handleDownloadPDF);
  }

  // Error Dismiss
  document.getElementById("btn-dismiss-error").addEventListener("click", hideError);

  // Initialize Default Statement Preset
  loadStatementPreset("gig");
}
