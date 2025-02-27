import React, { useState, useEffect } from "react";
import { auth, db } from "./script/firebase";
import { useNavigate } from "react-router-dom";
import { createUserWithEmailAndPassword, sendEmailVerification } from "firebase/auth";
import { ref, set, get, update, remove } from "firebase/database";
import "./index.css";

const ManageSecretary = () => {
  const navigate = useNavigate();
  const [secretary, setSecretary] = useState({ name: "", email: "", phone: "", password: "" });
  const [lawFirmAdmin, setLawFirmAdmin] = useState(null);
  const [existingSecretary, setExistingSecretary] = useState(null);

  useEffect(() => {
    const fetchAdminData = async () => {
      const user = auth.currentUser;
      if (user) {
        const adminRef = ref(db, `law_firm_admin/${user.uid}`);
        const snapshot = await get(adminRef);
        if (snapshot.exists()) {
          const adminData = snapshot.val();
          setLawFirmAdmin(adminData);
          await fetchSecretary(adminData.lawFirm);
        } else {
          alert("Error: Law firm admin not found!");
          navigate("/");
        }
      }
    };

    const fetchSecretary = async (lawFirm) => {
      const secRef = ref(db, `secretaries`);
      const snapshot = await get(secRef);
      if (snapshot.exists()) {
        const secretaries = snapshot.val();
        for (const uid in secretaries) {
          if (secretaries[uid].lawFirm === lawFirm) {
            setExistingSecretary({ uid, ...secretaries[uid] });
            setSecretary({
              name: secretaries[uid].name,
              email: secretaries[uid].email,
              phone: secretaries[uid].phone,
              password: ""
            });
            break;
          }
        }
      }
    };

    fetchAdminData();
  }, [navigate]);

  const addSecretary = async () => {
    if (!lawFirmAdmin) {
      alert("Law firm admin data not loaded. Please try again.");
      return;
    }

    try {
      const userCredential = await createUserWithEmailAndPassword(auth, secretary.email, secretary.password);
      const secretaryUID = userCredential.user.uid;

      await set(ref(db, `secretaries/${secretaryUID}`), {
        name: secretary.name,
        email: secretary.email,
        phone: secretary.phone,
        role: "secretary",
        lawFirm: lawFirmAdmin.lawFirm,
        adminUID: lawFirmAdmin.uid,
      });

      await sendEmailVerification(userCredential.user);
      alert("Secretary account created successfully! Verification email sent.");
      setExistingSecretary({ uid: secretaryUID, ...secretary });
      setSecretary({ ...secretary, password: "" });
    } catch (error) {
      alert("Error: " + error.message);
    }
  };

  const updateSecretary = async () => {
    if (existingSecretary) {
      await update(ref(db, `secretaries/${existingSecretary.uid}`), {
        name: secretary.name,
        phone: secretary.phone,
      });
      alert("Secretary details updated successfully.");
    }
  };

  const deleteSecretary = async () => {
    if (existingSecretary) {
      await remove(ref(db, `secretaries/${existingSecretary.uid}`));
      alert("Secretary account deleted successfully.");
      setSecretary({ name: "", email: "", phone: "", password: "" });
      setExistingSecretary(null);
    }
  };

  return (
    <div className="profile-card">
      <h2>Manage Secretary</h2>

      <input type="text" placeholder="Name" value={secretary.name} onChange={(e) => setSecretary({ ...secretary, name: e.target.value })} autoComplete="off" />
      <input type="email" placeholder="Email" value={secretary.email} disabled autoComplete="off" />
      <input type="password" placeholder="Password" value={secretary.password} onChange={(e) => setSecretary({ ...secretary, password: e.target.value })} autoComplete="new-password" disabled={!!existingSecretary} />
      <input type="text" placeholder="Phone" value={secretary.phone} onChange={(e) => setSecretary({ ...secretary, phone: e.target.value })} autoComplete="off" />

      {existingSecretary ? (
        <>
          <button onClick={updateSecretary} className="cancel-button">Update Secretary</button>
          <button onClick={deleteSecretary} className="cancel-button">Delete Secretary</button>
        </>
      ) : (
        <button onClick={addSecretary} className="cancel-button">Add Secretary</button>
      )}
      
      <button onClick={() => navigate("/")} className="cancel-button">Cancel</button>
    </div>
  );
};

export default ManageSecretary;
