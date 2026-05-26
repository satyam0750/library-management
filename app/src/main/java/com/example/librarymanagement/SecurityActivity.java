package com.example.librarymanagement;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.librarymanagement.database.DatabaseHelper;

public class SecurityActivity extends AppCompatActivity {

    EditText etOldPass, etNewPass, etConfirmPass;
    Button btnUpdate;

    DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security);

        etOldPass = findViewById(R.id.etOldPassword);
        etNewPass = findViewById(R.id.etNewPassword);
        etConfirmPass = findViewById(R.id.etConfirmPassword);
        btnUpdate = findViewById(R.id.btnUpdatePassword);

        db = new DatabaseHelper(this);

        btnUpdate.setOnClickListener(v -> {

            String oldPass = etOldPass.getText().toString();
            String newPass = etNewPass.getText().toString();
            String confirmPass = etConfirmPass.getText().toString();

            if(oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()){
                Toast.makeText(this,"Fill all fields",Toast.LENGTH_SHORT).show();
                return;
            }

            if(!newPass.equals(confirmPass)){
                Toast.makeText(this,"Passwords do not match",Toast.LENGTH_SHORT).show();
                return;
            }

            SQLiteDatabase database = db.getWritableDatabase();

            Cursor cursor = database.rawQuery(
                    "SELECT * FROM users WHERE password=?",
                    new String[]{oldPass}
            );

            if(cursor.moveToFirst()){

                database.execSQL(
                        "UPDATE users SET password=? WHERE password=?",
                        new Object[]{newPass, oldPass}
                );

                Toast.makeText(this,"Password Updated",Toast.LENGTH_SHORT).show();
                finish();

            }else{
                Toast.makeText(this,"Old Password Wrong",Toast.LENGTH_SHORT).show();
            }

            cursor.close();
        });
    }
}