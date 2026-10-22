"""
api/index.py - Flask Serverless API Entrypoint for PassBook & ScoreIQ

This file acts as the bridge between HTTP requests and the OOP domain models.
Designed for Vercel Serverless Functions:
- Vercel functions are stateless and spin up on demand.
- The frontend holds the customer ledger in localStorage and passes the full
  account dictionary with each transactional request.
- The backend deserializes the data into concrete Python class instances
  (SavingsAccount or CurrentAccount), executes domain methods, and returns
  the updated serialized state alongside the exact OOP method executed.
"""

import os
import sys
from flask import Flask, request, jsonify, send_from_directory

# Ensure the root directory containing models.py is in the Python module path
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(CURRENT_DIR, ".."))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from models import (
    Account,
    SavingsAccount,
    SeniorCitizenSavingsAccount,
    CurrentAccount,
    Transaction,
    ScoreIQ,
    BankError,
    InsufficientFundsError,
    MinimumBalanceError,
    WithdrawalLimitExceededError,
    InvalidAmountError,
)

app = Flask(__name__, static_folder="../public", static_url_path="")


# ============================================================================
# HELPER: Safe Account Rehydration
# ============================================================================

def get_rehydrated_account(account_payload: dict) -> Account:
    """
    Deserializes client-supplied JSON state into a concrete Account subclass.
    Demonstrates Polymorphism via factory instantiation.
    """
    if not account_payload or not isinstance(account_payload, dict):
        raise ValueError("Missing or invalid account data payload.")
    return Account.from_dict(account_payload)


# ============================================================================
# GLOBAL ERROR HANDLERS (Uniform JSON response format)
# ============================================================================

@app.errorhandler(BankError)
def handle_bank_error(error):
    return jsonify({"error": error.message, "type": error.__class__.__name__}), 400


@app.errorhandler(ValueError)
def handle_value_error(error):
    return jsonify({"error": str(error), "type": "ValidationError"}), 400


@app.errorhandler(Exception)
def handle_generic_error(error):
    return jsonify({"error": f"Internal server error: {str(error)}", "type": "ServerError"}), 500


# ============================================================================
# API ROUTES
# ============================================================================

@app.route("/api/classes", methods=["GET"])
def get_class_hierarchy():
    """
    Returns metadata about the OOP class hierarchy in models.py.
    Used by the frontend 'Under the Hood' inspector to show inheritance
    and method overriding dynamically.
    """
    hierarchy = [
        {
            "name": "Transaction",
            "parent": "object",
            "doc": "Immutable financial ledger entry with date, type, amount, balance, and description.",
            "methods": ["__init__", "to_dict", "from_dict"],
            "overrides": []
        },
        {
            "name": "Account",
            "parent": "object",
            "doc": "Base account with standard deposit, basic zero-floor withdrawal, and statement tracking.",
            "methods": ["__init__", "deposit", "withdraw", "transfer", "get_balance", "get_statement", "month_end", "describe", "to_dict", "from_dict"],
            "overrides": []
        },
        {
            "name": "SavingsAccount",
            "parent": "Account",
            "doc": "Subclass enforcing ₹500 minimum balance, max 5 withdrawals/month, and crediting 3.5% p.a. monthly interest.",
            "methods": ["withdraw", "month_end", "describe", "_get_current_month_withdrawal_count"],
            "overrides": ["withdraw", "month_end", "describe"]
        },
        {
            "name": "SeniorCitizenSavingsAccount",
            "parent": "SavingsAccount",
            "doc": "Multilevel hierarchy subclass (Account -> SavingsAccount -> SeniorCitizen). Credits preferential 4.25% p.a. interest.",
            "methods": ["month_end", "describe"],
            "overrides": ["month_end", "describe"]
        },
        {
            "name": "CurrentAccount",
            "parent": "Account",
            "doc": "Subclass allowing ₹10,000 overdraft protection and charging ₹100 flat monthly maintenance fee.",
            "methods": ["withdraw", "month_end", "describe"],
            "overrides": ["withdraw", "month_end", "describe"]
        },
        {
            "name": "ScoreIQ",
            "parent": "object",
            "doc": "Transparent rule-based scoring engine evaluating transaction activity, regularity, savings ratio, and stability.",
            "methods": ["calculate"],
            "overrides": []
        }
    ]
    return jsonify({"classes": hierarchy})


@app.route("/api/create", methods=["POST"])
def create_account():
    """
    Creates a new account instance based on type and opening balance.
    Payload: { "type": "savings"|"current"|"senior", "holder": "Name", "opening_balance": 1500.0 }
    """
    data = request.get_json(silent=True) or {}
    acc_type = (data.get("type") or "savings").strip().lower()
    holder = (data.get("holder") or "").strip()
    
    try:
        opening_balance = float(data.get("opening_balance", 0.0))
    except (ValueError, TypeError):
        return jsonify({"error": "Opening balance must be a valid number."}), 400

    if not holder:
        return jsonify({"error": "Account holder name is required."}), 400
    if opening_balance < 0:
        return jsonify({"error": "Opening balance cannot be negative."}), 400

    # Instantiate the appropriate subclass
    if "senior" in acc_type:
        if opening_balance < SavingsAccount.MINIMUM_BALANCE:
            return jsonify({
                "error": f"Senior Citizen accounts require an opening deposit of at least ₹{SavingsAccount.MINIMUM_BALANCE:,.2f}."
            }), 400
        account = SeniorCitizenSavingsAccount(holder=holder, balance=0.0)
        method_used = "SeniorCitizenSavingsAccount.__init__ (Multilevel Derived)"
    elif "saving" in acc_type:
        if opening_balance < SavingsAccount.MINIMUM_BALANCE:
            return jsonify({
                "error": f"Savings accounts require an opening deposit of at least ₹{SavingsAccount.MINIMUM_BALANCE:,.2f}."
            }), 400
        account = SavingsAccount(holder=holder, balance=0.0)
        method_used = "SavingsAccount.__init__"
    elif "current" in acc_type:
        account = CurrentAccount(holder=holder, balance=0.0)
        method_used = "CurrentAccount.__init__"
    else:
        account = Account(holder=holder, balance=0.0)
        method_used = "Account.__init__"

    # Log initial opening deposit if provided
    if opening_balance > 0:
        account.deposit(opening_balance, description="Initial Opening Deposit")

    return jsonify({
        "account": account.to_dict(),
        "method_used": method_used,
        "description": account.describe(),
        "message": f"Account {account.account_number} created successfully for {account.holder}."
    })


@app.route("/api/deposit", methods=["POST"])
def deposit_funds():
    """
    Executes a deposit on the account.
    Payload: { "account": {...}, "amount": 2500.0, "description": "Client Payment" }
    """
    data = request.get_json(silent=True) or {}
    account = get_rehydrated_account(data.get("account"))
    
    try:
        amount = float(data.get("amount", 0.0))
    except (ValueError, TypeError):
        return jsonify({"error": "Deposit amount must be a valid number."}), 400

    custom_desc = (data.get("description") or "").strip() or "Cash Deposit"
    
    # Polymorphic method call
    tx = account.deposit(amount, description=custom_desc)

    return jsonify({
        "account": account.to_dict(),
        "transaction": tx.to_dict(),
        "method_used": f"{account.__class__.__name__}.deposit (inherited from Account)",
        "message": f"Deposited ₹{amount:,.2f} successfully. New balance: ₹{account.balance:,.2f}."
    })


@app.route("/api/withdraw", methods=["POST"])
def withdraw_funds():
    """
    Executes a withdrawal on the account.
    Payload: { "account": {...}, "amount": 1000.0, "description": "ATM Withdrawal" }
    """
    data = request.get_json(silent=True) or {}
    account = get_rehydrated_account(data.get("account"))
    
    try:
        amount = float(data.get("amount", 0.0))
    except (ValueError, TypeError):
        return jsonify({"error": "Withdrawal amount must be a valid number."}), 400

    custom_desc = (data.get("description") or "").strip() or "Cash Withdrawal"

    # Polymorphic method call (triggers overridden rules in SavingsAccount or CurrentAccount)
    tx = account.withdraw(amount, description=custom_desc)

    is_overridden = account.__class__ in (SavingsAccount, CurrentAccount)
    override_note = " (overridden from Account)" if is_overridden else ""

    return jsonify({
        "account": account.to_dict(),
        "transaction": tx.to_dict(),
        "method_used": f"{account.__class__.__name__}.withdraw{override_note}",
        "message": f"Withdrew ₹{amount:,.2f} successfully. New balance: ₹{account.balance:,.2f}."
    })


@app.route("/api/transfer", methods=["POST"])
def transfer_funds():
    """
    Executes an inter-account transfer between two accounts.
    Payload: { "source_account": {...}, "target_account": {...}, "amount": 500.0 }
    """
    data = request.get_json(silent=True) or {}
    src_account = get_rehydrated_account(data.get("source_account"))
    dest_account = get_rehydrated_account(data.get("target_account"))

    try:
        amount = float(data.get("amount", 0.0))
    except (ValueError, TypeError):
        return jsonify({"error": "Transfer amount must be a valid number."}), 400

    # Executes transfer using source's withdraw() and target's deposit()
    src_tx, dest_tx = src_account.transfer(dest_account, amount)

    return jsonify({
        "source_account": src_account.to_dict(),
        "target_account": dest_account.to_dict(),
        "source_transaction": src_tx.to_dict(),
        "target_transaction": dest_tx.to_dict(),
        "method_used": f"{src_account.__class__.__name__}.transfer -> {src_account.__class__.__name__}.withdraw() & {dest_account.__class__.__name__}.deposit()",
        "message": f"Transferred ₹{amount:,.2f} from {src_account.holder} to {dest_account.holder}."
    })


@app.route("/api/month-end", methods=["POST"])
def run_month_end():
    """
    Executes month-end processing (interest calculation or maintenance fees).
    Payload: { "account": {...} }
    """
    data = request.get_json(silent=True) or {}
    account = get_rehydrated_account(data.get("account"))

    old_balance = account.balance
    tx = account.month_end()

    is_overridden = account.__class__ in (SavingsAccount, CurrentAccount)
    override_note = " (overridden from Account)" if is_overridden else ""

    if tx is None:
        message = f"Month-end processed for {account.holder}. No balance adjustments required."
    elif tx.tx_type == "INTEREST":
        message = f"Credited monthly savings interest of ₹{tx.amount:,.2f} @ 3.5% p.a. New balance: ₹{account.balance:,.2f}."
    elif tx.tx_type == "FEE":
        message = f"Deducted monthly current account maintenance fee of ₹{tx.amount:,.2f}. New balance: ₹{account.balance:,.2f}."
    else:
        message = f"Month-end executed. New balance: ₹{account.balance:,.2f}."

    return jsonify({
        "account": account.to_dict(),
        "transaction": tx.to_dict() if tx else None,
        "method_used": f"{account.__class__.__name__}.month_end{override_note}",
        "message": message
    })


@app.route("/api/statement", methods=["POST"])
def get_statement():
    """
    Retrieves the chronological statement history for the provided account.
    Payload: { "account": {...} }
    """
    data = request.get_json(silent=True) or {}
    account = get_rehydrated_account(data.get("account"))

    return jsonify({
        "account_number": account.account_number,
        "holder": account.holder,
        "type": account.ACCOUNT_TYPE,
        "balance": account.get_balance(),
        "statement": account.get_statement(),
        "method_used": f"{account.__class__.__name__}.get_statement"
    })


@app.route("/api/scoreiq", methods=["POST"])
def calculate_scoreiq():
    """
    Evaluates alternative credit scoring with manual user_data / bank_data or active account.
    """
    data = request.get_json(silent=True) or {}
    user_data = data.get("user_data")
    bank_data = data.get("bank_data")
    account = None
    if data.get("account"):
        account = get_rehydrated_account(data.get("account"))

    score_data = ScoreIQ.calculate(account=account, user_data=user_data, bank_data=bank_data)
    score_data["method_used"] = "ScoreIQ.calculate (CreditScoreEngine)"
    if account:
        score_data["account_number"] = account.account_number
        score_data["holder"] = account.holder

    return jsonify(score_data)


@app.route("/api/scoreiq/statement", methods=["POST"])
def review_external_statement():
    """
    Parses an external bank statement file or text and returns an underwriting review.
    Payload: { "statement_text": "...", "applicant_name": "...", "employment_type": "..." }
    """
    data = request.get_json(silent=True) or {}
    statement_text = data.get("statement_text") or ""
    applicant_name = data.get("applicant_name") or "External Applicant"
    employment_type = data.get("employment_type") or "gig worker"

    review = ScoreIQ.review_statement(statement_text, applicant_name, employment_type)
    return jsonify(review)


# ============================================================================
# STATIC FILE SERVING (For local development or direct Flask execution)
# ============================================================================

@app.route("/", defaults={"path": ""})
@app.route("/<path:path>")
def serve_static(path):
    static_folder = app.static_folder
    if path and os.path.exists(os.path.join(static_folder, path)):
        return send_from_directory(static_folder, path)
    return send_from_directory(static_folder, "index.html")


if __name__ == "__main__":
    app.run(debug=True, port=5000)
