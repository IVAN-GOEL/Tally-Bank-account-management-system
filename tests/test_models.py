"""
test_models.py - Unit & Integration Test Suite for Tally & ScoreIQ

Tests:
1. Core OOP inheritance, encapsulation, and polymorphism.
2. SavingsAccount specific method overriding (min balance, withdrawal count, interest).
3. CurrentAccount specific method overriding (overdraft up to ₹10,000, maintenance fee).
4. Inter-account transfer atomicity.
5. ScoreIQ alternative credit calculation (factors, caps, band mapping).
6. Flask serverless API endpoints and error status codes.
"""

import unittest
from models import (
    Transaction,
    Account,
    SavingsAccount,
    CurrentAccount,
    ScoreIQ,
    BankError,
    InsufficientFundsError,
    MinimumBalanceError,
    WithdrawalLimitExceededError,
    InvalidAmountError,
)
from api.index import app


class TestTransactionModel(unittest.TestCase):
    def test_transaction_creation_and_dict(self):
        tx = Transaction("DEPOSIT", 1500.50, 4500.50, description="Salary credit")
        self.assertEqual(tx.tx_type, "DEPOSIT")
        self.assertEqual(tx.amount, 1500.50)
        self.assertEqual(tx.balance_after, 4500.50)
        self.assertEqual(tx.description, "Salary credit")

        d = tx.to_dict()
        self.assertEqual(d["type"], "DEPOSIT")
        self.assertEqual(d["amount"], 1500.50)

        # Deserialization test
        rehydrated = Transaction.from_dict(d)
        self.assertEqual(rehydrated.amount, 1500.50)
        self.assertEqual(rehydrated.balance_after, 4500.50)


class TestBaseAccount(unittest.TestCase):
    def setUp(self):
        self.acc = Account(holder="Ravi Sharma", balance=2000.0)

    def test_initial_state(self):
        self.assertEqual(self.acc.holder, "Ravi Sharma")
        self.assertEqual(self.acc.balance, 2000.0)
        self.assertTrue(self.acc.account_number.startswith("PB"))
        self.assertEqual(len(self.acc.transactions), 0)

    def test_deposit_valid(self):
        tx = self.acc.deposit(500.0, "Cash deposit")
        self.assertEqual(self.acc.balance, 2500.0)
        self.assertEqual(len(self.acc.transactions), 1)
        self.assertEqual(tx.amount, 500.0)

    def test_deposit_invalid_amount(self):
        with self.assertRaises(InvalidAmountError):
            self.acc.deposit(-100.0)
        with self.assertRaises(InvalidAmountError):
            self.acc.deposit(0)

    def test_base_withdraw_valid(self):
        self.acc.withdraw(500.0)
        self.assertEqual(self.acc.balance, 1500.0)

    def test_base_withdraw_insufficient_funds(self):
        with self.assertRaises(InsufficientFundsError):
            self.acc.withdraw(3000.0)

    def test_transfer(self):
        target = Account(holder="Priya Patel", balance=1000.0)
        src_tx, dest_tx = self.acc.transfer(target, 500.0)
        self.assertEqual(self.acc.balance, 1500.0)
        self.assertEqual(target.balance, 1500.0)
        self.assertIn("Priya Patel", src_tx.description)
        self.assertIn("Ravi Sharma", dest_tx.description)

    def test_cannot_transfer_to_self(self):
        with self.assertRaises(BankError):
            self.acc.transfer(self.acc, 200.0)


class TestSavingsAccountOverriding(unittest.TestCase):
    def setUp(self):
        self.savings = SavingsAccount(holder="Ananya Verma", balance=5000.0)

    def test_minimum_balance_enforcement(self):
        # Balance is 5000. Minimum balance is 500. Can withdraw up to 4500.
        self.savings.withdraw(3500.0)
        self.assertEqual(self.savings.balance, 1500.0)

        # Attempting to withdraw 1100 leaves 400 (< 500), which must raise MinimumBalanceError
        with self.assertRaises(MinimumBalanceError):
            self.savings.withdraw(1100.0)

    def test_withdrawal_count_limit(self):
        # 5 withdrawals allowed in a month
        for i in range(5):
            self.savings.withdraw(100.0)
        self.assertEqual(self.savings.balance, 4500.0)

        # 6th withdrawal must be blocked
        with self.assertRaises(WithdrawalLimitExceededError):
            self.savings.withdraw(100.0)

    def test_month_end_interest_calculation(self):
        # Annual rate is 3.5%, balance is 12,000 -> monthly interest = 12000 * 0.035 / 12 = 35.0
        acc = SavingsAccount(holder="Test", balance=12000.0)
        tx = acc.month_end()
        self.assertIsNotNone(tx)
        self.assertEqual(tx.tx_type, "INTEREST")
        self.assertEqual(tx.amount, 35.0)
        self.assertEqual(acc.balance, 12035.0)

    def test_describe_polymorphism(self):
        desc = self.savings.describe()
        self.assertIn("3.5%", desc)
        self.assertIn("500", desc)


class TestCurrentAccountOverriding(unittest.TestCase):
    def setUp(self):
        self.current = CurrentAccount(holder="Kavita Enterprises", balance=2000.0)

    def test_overdraft_allowance(self):
        # Balance is 2000, overdraft allows up to -10,000. Can withdraw up to 12,000.
        tx = self.current.withdraw(5000.0)
        self.assertEqual(self.current.balance, -3000.0)
        self.assertEqual(tx.amount, 5000.0)

        # Withdraw further down to -9500
        self.current.withdraw(6500.0)
        self.assertEqual(self.current.balance, -9500.0)

        # Attempting to withdraw another 600 exceeds -10000 limit -> InsufficientFundsError
        with self.assertRaises(InsufficientFundsError):
            self.current.withdraw(600.0)

    def test_month_end_maintenance_fee(self):
        tx = self.current.month_end()
        self.assertIsNotNone(tx)
        self.assertEqual(tx.tx_type, "FEE")
        self.assertEqual(tx.amount, 100.0)
        self.assertEqual(self.current.balance, 1900.0)

    def test_describe_polymorphism(self):
        desc = self.current.describe()
        self.assertIn("10,000", desc)
        self.assertIn("100", desc)


class TestScoreIQAlgorithm(unittest.TestCase):
    def test_scoreiq_disciplined_account(self):
        acc = SavingsAccount(holder="Aarav Mehta", balance=6000.0)
        acc.deposit(2000.0, "Deposit 1")
        acc.deposit(3000.0, "Deposit 2")
        acc.deposit(2500.0, "Deposit 3")
        acc.deposit(1500.0, "Deposit 4")
        acc.withdraw(1000.0, "Groceries")
        acc.withdraw(500.0, "Utilities")

        result = ScoreIQ.calculate(acc)
        self.assertGreaterEqual(result["score"], 300)
        self.assertLessEqual(result["score"], 900)
        self.assertIn(result["band"], ["Good", "Excellent"])
        self.assertEqual(len(result["breakdown"]), 4)
        self.assertGreater(len(result["tips"]), 0)

    def test_scoreiq_overdrafted_account(self):
        acc = CurrentAccount(holder="Stressed Trader", balance=-4000.0)
        result = ScoreIQ.calculate(acc)
        self.assertLessEqual(result["score"], 650)
        # Check stability breakdown reflects overdraft penalty
        stability = next(f for f in result["breakdown"] if f["name"] == "Account Stability")
        self.assertIn("overdraft", stability["explanation"].lower())


class TestFlaskEndpoints(unittest.TestCase):
    def setUp(self):
        self.client = app.test_client()

    def test_classes_endpoint(self):
        res = self.client.get("/api/classes")
        self.assertEqual(res.status_code, 200)
        data = res.get_json()
        names = [c["name"] for c in data["classes"]]
        self.assertIn("Account", names)
        self.assertIn("SavingsAccount", names)
        self.assertIn("CurrentAccount", names)
        self.assertIn("ScoreIQ", names)

    def test_create_savings_endpoint(self):
        payload = {"type": "savings", "holder": "Vikram Singh", "opening_balance": 1500.0}
        res = self.client.post("/api/create", json=payload)
        self.assertEqual(res.status_code, 200)
        data = res.get_json()
        self.assertEqual(data["account"]["type"], "Savings Account")
        self.assertEqual(data["account"]["balance"], 1500.0)
        self.assertEqual(data["method_used"], "SavingsAccount.__init__")

    def test_create_savings_insufficient_opening_balance(self):
        payload = {"type": "savings", "holder": "Vikram Singh", "opening_balance": 200.0}
        res = self.client.post("/api/create", json=payload)
        self.assertEqual(res.status_code, 400)
        self.assertIn("at least ₹500", res.get_json()["error"])

    def test_deposit_and_withdraw_endpoints(self):
        # Create savings account
        create_res = self.client.post("/api/create", json={"type": "savings", "holder": "Sunita", "opening_balance": 2000.0})
        acc_dict = create_res.get_json()["account"]

        # Deposit
        dep_res = self.client.post("/api/deposit", json={"account": acc_dict, "amount": 1000.0, "description": "Gift"})
        self.assertEqual(dep_res.status_code, 200)
        updated_acc = dep_res.get_json()["account"]
        self.assertEqual(updated_acc["balance"], 3000.0)

        # Withdraw
        w_res = self.client.post("/api/withdraw", json={"account": updated_acc, "amount": 1200.0})
        self.assertEqual(w_res.status_code, 200)
        self.assertEqual(w_res.get_json()["account"]["balance"], 1800.0)
        self.assertIn("SavingsAccount.withdraw", w_res.get_json()["method_used"])

    def test_month_end_endpoint(self):
        create_res = self.client.post("/api/create", json={"type": "current", "holder": "Kiran Store", "opening_balance": 5000.0})
        acc_dict = create_res.get_json()["account"]

        me_res = self.client.post("/api/month-end", json={"account": acc_dict})
        self.assertEqual(me_res.status_code, 200)
        self.assertEqual(me_res.get_json()["account"]["balance"], 4900.0)
        self.assertIn("CurrentAccount.month_end", me_res.get_json()["method_used"])

    def test_scoreiq_endpoint(self):
        create_res = self.client.post("/api/create", json={"type": "savings", "holder": "Devraj", "opening_balance": 4000.0})
        acc_dict = create_res.get_json()["account"]

        score_res = self.client.post("/api/scoreiq", json={"account": acc_dict})
        self.assertEqual(score_res.status_code, 200)
        res_data = score_res.get_json()
        self.assertIn("score", res_data)
        self.assertIn("band", res_data)
        self.assertIn("breakdown", res_data)


if __name__ == "__main__":
    unittest.main()
