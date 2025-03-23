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
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    const fetchAdminData = async () => {
      setIsLoading(true);
      const user = auth.currentUser;
      if (user) {
        const adminRef = ref(db, `law_firm_admin/${user.uid}`);
        const snapshot = await get(adminRef);
        if (snapshot.exists()) {
          const adminData = snapshot.val();
          setLawFirmAdmin(adminData);
          await fetchSecretary(adminData.lawFirm);
        } else {
          showNotification("Error: Law firm admin not found!", "error");
          navigate("/");
        }
      }
      setIsLoading(false);
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

  const showNotification = (message, type = "success") => {
    alert(message); // Replace with toast notification in a real app
  };

  const addSecretary = async () => {
    if (!lawFirmAdmin) {
      showNotification("Law firm admin data not loaded. Please try again.", "error");
      return;
    }

    if (!secretary.email || !secretary.password) {
      showNotification("Please enter both email and password for the new secretary.", "error");
      return;
    }

    setIsLoading(true);
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
      showNotification("Secretary account created successfully! Verification email sent.");

      setExistingSecretary({ uid: secretaryUID, ...secretary });
      setSecretary({ name: "", email: "", phone: "", password: "" });
    } catch (error) {
      showNotification("Error: " + error.message, "error");
    }
    setIsLoading(false);
  };

  const enableEditing = () => {
    setIsEditing(true);
  };

  const saveSecretaryChanges = async () => {
    if (existingSecretary) {
      setIsLoading(true);
      try {
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

        showNotification("Secretary details updated successfully.");
        setIsEditing(false);
      } catch (error) {
        showNotification("Error updating secretary: " + error.message, "error");
      }
      setIsLoading(false);
    }
  };

  const deleteSecretary = async () => {
    if (existingSecretary) {
      if (window.confirm("Are you sure you want to delete this secretary account?")) {
        setIsLoading(true);
        try {
          await remove(ref(db, `secretaries/${existingSecretary.uid}`));
          showNotification("Secretary account deleted successfully.");
          setSecretary({ name: "", email: "", phone: "", password: "" });
          setExistingSecretary(null);
        } catch (error) {
          showNotification("Error deleting secretary: " + error.message, "error");
        }
        setIsLoading(false);
      }
    }
  };

  return (
    <div className="secretary-container">
    {/* Cancel button above the header */}
    <button onClick={() => navigate("/")} className="back-button">
      <span className="icon-back"></span>
    </button>
      
      <div className="secretary-header">
        <h2 className="secretary-title">Manage Secretary</h2>
      </div>

      {isLoading ? (
        <div className="loading-spinner">
          <div className="spinner"></div>
        </div>
      ) : existingSecretary && !isEditing ? (
        <div className="secretary-details">
          <div className="detail-row">
            <p className="detail-label">Name:</p>
            <p className="detail-value">{secretary.name}</p>
          </div>
          
          <div className="detail-row">
            <p className="detail-label">Email:</p>
            <p className="detail-value">{secretary.email}</p>
          </div>
          
          <div className="detail-row">
            <p className="detail-label">Phone:</p>
            <p className="detail-value">{secretary.phone}</p>
          </div>
          
          <div className="btn-container">
            <button onClick={enableEditing} className="btn btn-primary">
              <span className="icon-edit"></span>
              Update Secretary
            </button>
            
            <button onClick={deleteSecretary} className="btn btn-danger">
              <span className="icon-delete"></span>
              Delete Secretary
            </button>
          </div>
        </div>
      ) : (
        <div className="secretary-form">
          <div className="form-group">
            <input
              type="text"
              placeholder="Name"
              value={secretary.name}
              onChange={(e) => setSecretary({ ...secretary, name: e.target.value })}
              autoComplete="off"
              className="form-input"
              style={{ color: "black" }}
            />
          </div>
          
          <div className="form-group">
            <input
              type="email"
              placeholder="Email"
              value={secretary.email}
              onChange={(e) => setSecretary({ ...secretary, email: e.target.value })}
              autoComplete="off"
              className="form-input"
              style={{ color: "black" }}
            />
          </div>
          
          <div className="form-group">
            <input
              type="password"
              placeholder="Password"
              value={secretary.password}
              onChange={(e) => setSecretary({ ...secretary, password: e.target.value })}
              autoComplete="new-password"
              className="form-input"
              style={{ color: "black" }}
            />
          </div>
          
          <div className="form-group">
            <input
              type="text"
              placeholder="Phone"
              value={secretary.phone}
              onChange={(e) => setSecretary({ ...secretary, phone: e.target.value })}
              autoComplete="off"
              className="form-input"
              style={{ color: "black" }}
            />
          </div>
          
          {existingSecretary ? (
            <button onClick={saveSecretaryChanges} className="btn btn-primary">
              <span className="icon-save"></span>
              Save Changes
            </button>
          ) : (
            <button onClick={addSecretary} className="btn btn-primary">
              <span className="icon-add"></span>
              Add Secretary
            </button>
          )}
        </div>
      )}
    </div>
  );
};

export default ManageSecretary;