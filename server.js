const express = require("express");
const Stripe = require("stripe");
const cors = require("cors");

const app = express();
const stripe = Stripe("sk_test_51R1s9U4Ib6pQtdzfYEqmLkpUBiSsRWx4WJJNPnnMrguQwfvLdkS9xlhnFXPUPmRf0YdzmU1TGJLLBUDkH2Ka3gE4004fuIEAKu");

app.use(express.json());
app.use(cors());

// Add a new endpoint for Payment Intent
app.post("/create-payment-intent", async (req, res) => {
  try {
    const { amount, currency } = req.body;
    
    const paymentIntent = await stripe.paymentIntents.create({
      amount,
      currency,
      automatic_payment_methods: {
        enabled: true,
      },
    });

    res.json({ clientSecret: paymentIntent.client_secret });
  } catch (error) {
    console.error("Error creating payment intent:", error);
    res.status(500).json({ error: error.message });
  }
});

// Keep the existing checkout session endpoint
app.post("/create-checkout-session", async (req, res) => {
    try {
        const { amount } = req.body;

        const session = await stripe.checkout.sessions.create({
            payment_method_types: ["card"],
            line_items: [
                {
                    price_data: {
                        currency: "php",
                        product_data: { name: "Subscription" },
                        unit_amount: amount,
                    },
                    quantity: 1,
                },
            ],
            mode: "payment",
            success_url: "http://localhost:5173/payment-success",
            cancel_url: "http://localhost:5173/payment-cancelled",
        });

        res.json({ id: session.id });
    } catch (error) {
        console.error("Error creating checkout session:", error);
        res.status(500).json({ error: error.message });
    }
});

const PORT = 5000;
app.listen(PORT, () => console.log(`Server running on port ${PORT}`));