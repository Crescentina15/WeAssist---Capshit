import React, { useState, useEffect } from "react";
import { auth, db } from "./script/firebase";
import { useNavigate } from "react-router-dom";
import { createUserWithEmailAndPassword, sendEmailVerification, updatePassword } from "firebase/auth";
import { ref, set, get, update, remove } from "firebase/database";
import "./index.css";

const ManageSecretary = () => {
  const navigate = useNavigate();
  const [secretary, setSecretary] = useState({ name: "", email: "", phone: "", password: "" });
  const [lawFirmAdmin, setLawFirmAdmin] = useState(null);
  const [existingSecretary, setExistingSecretary] = useState(null);
  const [isEditing, setIsEditing] = useState(false);

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

    if (!secretary.email || !secretary.password) {
      alert("Please enter both email and password for the new secretary.");
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
      setSecretary({ name: "", email: "", phone: "", password: "" });
    } catch (error) {
      alert("Error: " + error.message);
    }
  };

  const enableEditing = () => {
    setIsEditing(true);
  };

  const saveSecretaryChanges = async () => {
    if (existingSecretary) {
      await update(ref(db, `secretaries/${existingSecretary.uid}`), {
        name: secretary.name,
        phone: secretary.phone,
        email: secretary.email
      });

      if (secretary.password) {
        const user = auth.currentUser;
        if (user) {
          await updatePassword(user, secretary.password);
        }
      }

      alert("Secretary details updated successfully.");
      setIsEditing(false);
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

      {existingSecretary && !isEditing ? (
        <div className="profile-display">
          <p><strong>Name:</strong> {secretary.name}</p>
          <p><strong>Email:</strong> {secretary.email}</p>
          <p><strong>Phone:</strong> {secretary.phone}</p>
          <button onClick={enableEditing} className="cancel-button">Update Secretary</button>
          <button onClick={deleteSecretary} className="cancel-button">Delete Secretary</button>
        </div>
      ) : (
        <div>
          <input
            type="text"
            placeholder="Name"
            value={secretary.name}
            onChange={(e) => setSecretary({ ...secretary, name: e.target.value })}
            autoComplete="off"
          />
          <input
            type="email"
            placeholder="Email"
            value={secretary.email}
            onChange={(e) => setSecretary({ ...secretary, email: e.target.value })}
            autoComplete="off"
          />
          <input
            type="password"
            placeholder="Password"
            value={secretary.password}
            onChange={(e) => setSecretary({ ...secretary, password: e.target.value })}
            autoComplete="new-password"
          />
          <input
            type="text"
            placeholder="Phone"
            value={secretary.phone}
            onChange={(e) => setSecretary({ ...secretary, phone: e.target.value })}
            autoComplete="off"
          />
          {existingSecretary ? (
            <button onClick={saveSecretaryChanges} className="cancel-button">Save Changes</button>
          ) : (
            <button onClick={addSecretary} className="cancel-button">Add Secretary</button>
          )}
        </div>
      )}

      <button onClick={() => navigate("/")} className="cancel-button">Cancel</button>
    </div>
  );
};

export default ManageSecretary;
