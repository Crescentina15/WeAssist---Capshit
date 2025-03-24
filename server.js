// server.js
const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const stripe = require('stripe')('sk_test_51R1JB1FK88cwX0GIAtBQPltbku8RJzsK5bgRifpIhgAwJsv72YUTMXwM4zwIxy3uSSmOkFJjcn4quoD8yhc9pIAS00efhRhNFJ');

const app = express();
const port = process.env.PORT || 5000;

const corsOptions = {
  origin: 'http://localhost:5174', // Changed from 5173 to 5174
  methods: ['GET', 'POST', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization'],
  credentials: true
};

// Middleware
app.use(cors(corsOptions));
app.use(bodyParser.json());

// In-memory plan data (in a real app, you'd use a database)
const plans = [
  {
    id: "plan_1month",
    name: "1 Month Plan",
    price: "₱500",
    amount: 500,
    description: "Access premium features for one month.",
  },
  {
    id: "plan_6months",
    name: "6 Months Plan",
    price: "₱2,500",
    amount: 2500,
    description: "Enjoy premium features for six months at a discounted rate.",
  },
  {
    id: "plan_1year",
    name: "1 Year Plan",
    price: "₱4,800",
    amount: 4800,
    description: "Get the best value with a full-year subscription.",
  },
];

// Get all plans
app.get('/api/plans', (req, res) => {
  res.json(plans);
});

// Get a specific plan by ID
app.get('/api/plans/:id', (req, res) => {
  const planId = req.params.id;
  const plan = plans.find(p => p.id === planId);
  
  if (!plan) {
    return res.status(404).json({ error: 'Plan not found' });
  }
  
  res.json(plan);
});

// Create a payment intent (keeping this for backward compatibility)
app.post('/create-payment-intent', async (req, res) => {
  try {
    const { amount, planName, planId, customer_name, customer_email } = req.body;
    
    // Validate input
    if (!amount || !planName || !customer_email) {
      return res.status(400).json({ error: 'Missing required fields' });
    }

    // Create a customer in Stripe
    const customer = await stripe.customers.create({
      name: customer_name,
      email: customer_email,
      metadata: {
        planId,
        planName
      }
    });

    // Create a payment intent
    const paymentIntent = await stripe.paymentIntents.create({
      amount: amount * 100, // Convert to cents/smallest currency unit
      currency: 'php',
      customer: customer.id,
      metadata: {
        planId,
        planName
      },
      description: `Subscription for ${planName}`
    });

    res.json({
      clientSecret: paymentIntent.client_secret
    });
  } catch (error) {
    console.error('Error creating payment intent:', error);
    res.status(500).json({ error: error.message });
  }
});

// NEW: Create a Checkout Session
// Update the create-checkout-session endpoint
app.post('/create-checkout-session', async (req, res) => {
  try {
    console.log('Request received:', req.body);
    const { planId, planName, amount } = req.body;
    
    // Validate input
    if (!amount || !planName || !planId) {
      console.error('Missing required fields:', { planId, planName, amount });
      return res.status(400).json({ error: 'Missing required fields' });
    }

    // Create a new Checkout Session
    const session = await stripe.checkout.sessions.create({
      payment_method_types: ['card'],
      line_items: [
        {
          price_data: {
            currency: 'php',
            product_data: {
              name: planName,
            },
            unit_amount: amount * 100, // Stripe uses cents/smallest currency unit
          },
          quantity: 1,
        },
      ],
      mode: 'payment',
      success_url: `http://localhost:5174/payment-success?session_id={CHECKOUT_SESSION_ID}`,
      cancel_url: `http://localhost:5174/plans`,
      metadata: {
        planId: planId,
      },
    });

    console.log('Session created successfully:', session.id);
    res.json({ id: session.id });
  } catch (error) {
    console.error('Detailed checkout session error:', error);
    res.status(500).json({ error: error.message });
  }
});

// Handle webhook events from Stripe
app.post('/webhook', 
  bodyParser.raw({ type: 'application/json' }), 
  async (req, res) => {
    const sig = req.headers['stripe-signature'];
    const endpointSecret = 'your_webhook_signing_secret'; // Replace with your webhook signing secret
    
    let event;
    
    try {
      event = stripe.webhooks.constructEvent(req.body, sig, endpointSecret);
    } catch (err) {
      return res.status(400).send(`Webhook Error: ${err.message}`);
    }
    
    // Handle different event types
    switch (event.type) {
      case 'payment_intent.succeeded':
        const paymentIntent = event.data.object;
        console.log('Payment succeeded:', paymentIntent.id);
        // Handle the successful payment (e.g., update database, send email)
        break;
        
      case 'checkout.session.completed':
        const session = event.data.object;
        console.log('Checkout completed:', session.id);
        // Handle the completed checkout (e.g., fulfill order, activate subscription)
        
        // Example: Get the plan ID from metadata and process accordingly
        const planId = session.metadata.planId;
        console.log(`Plan ID: ${planId}`);
        break;
        
      default:
        console.log(`Unhandled event type: ${event.type}`);
    }
    
    res.json({ received: true });
});

app.listen(port, () => {
  console.log(`Server running on port ${port}`);
});