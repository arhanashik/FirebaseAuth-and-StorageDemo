package com.blackspider.firebaseauthdemo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.IOException;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {

    private static final int CHOOSE_IMAGE = 11;

    @BindView(R.id.txt_display_name) TextView txtDisplayName;
    @BindView(R.id.img_profile) ImageView imgProfile;
    @BindView(R.id.txt_verification) TextView txtVerification;
    @BindView(R.id.fab) FloatingActionButton _fab;

    private ProgressBar loadingView;
    private ImageView userImage;
    private Uri imageUri;

    private FirebaseAuth mAuth;
    private FirebaseUser mUser;
    private FirebaseStorage mStorage;
    private String imgRef = "img/pic_" + System.currentTimeMillis() + ".jpg";
    private Uri profileImageUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mAuth = FirebaseAuth.getInstance();
        mUser = mAuth.getCurrentUser();
        mStorage = FirebaseStorage.getInstance();

        loadProfile();

        _fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showUserInfoDialog();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.action_resend_verification_email:
                sendVerificationEmail();
                return true;

            case R.id.action_reset_email:
                resetEmail();
                return true;

            case R.id.action_reset_password:
                resetPassword();
                return true;

            case R.id.action_send_password_resent_email:
                sendPasswordResetEmail();
                return true;

            case R.id.action_re_auth:
                reAuthUser();
                return true;

            case R.id.action_sign_out:
                signOut();
                return true;

            case R.id.action_delete_user:
                deleteUser();
                return true;

            default:
                return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == CHOOSE_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null){
            imageUri = data.getData();

            try {
                Bitmap imgBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                userImage.setImageBitmap(imgBitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }

            loadingView.setVisibility(View.VISIBLE);
            uploadImage();
        }
    }

    private void loadProfile(){
        if(mUser != null) {
            String displayName = mUser.getDisplayName();
            Uri profileImageUrl = mUser.getPhotoUrl();

            setTitle(displayName);
            txtDisplayName.setText(displayName);

            RequestOptions requestOptions = new RequestOptions()
                    .placeholder(R.drawable.app_icon)
                    .circleCrop()
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.ALL);

            Glide.with(this)
                    .load(profileImageUrl)
                    .apply(requestOptions)
                    .into(imgProfile);

            if(mUser.isEmailVerified()) txtVerification.setText("Verified Account!");
            else {
                sendVerificationEmail();
            }
        }
    }

    private void showUserInfoDialog(){
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.prompt_user_info, null);

        loadingView = view.findViewById(R.id.pb_loading);
        userImage = view.findViewById(R.id.btn_choose_image);
        final EditText etDisplayName = view.findViewById(R.id.et_display_name);
        Button btnSave = view.findViewById(R.id.btn_save_info);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        dialog.show();

        userImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImageChooser();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String displayName = etDisplayName.getText().toString();
                if(TextUtils.isEmpty(displayName)) {
                    etDisplayName.setError("display name is required!");
                    etDisplayName.requestFocus();
                }
                else {
                    updateProfile(profileImageUrl, displayName);
                }
            }
        });
    }

    private void showImageChooser(){
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);

        startActivityForResult(Intent.createChooser(intent, "Image chooser"), CHOOSE_IMAGE);
    }

    private void uploadImage(){
        if(imageUri != null) {
            StorageReference mReference = mStorage.getReference(imgRef);

            mReference.putFile(imageUri)
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            loadingView.setVisibility(View.INVISIBLE);

                            profileImageUrl = taskSnapshot.getDownloadUrl();
                            Toast.makeText(MainActivity.this, "Image selected!", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            loadingView.setVisibility(View.INVISIBLE);

                            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void updateProfile(Uri profileImageUrl, String displayName) {
        if(mUser != null){
            UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .setPhotoUri(profileImageUrl)
                    .build();

            mUser.updateProfile(request)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if(task.isSuccessful()){
                                loadProfile();
                                Toast.makeText(MainActivity.this, "Profile updated!", Toast.LENGTH_SHORT).show();
                            }
                            else {
                                Toast.makeText(MainActivity.this, task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }

    private void sendVerificationEmail() {
        txtVerification.setText("Account not verified! Click here for verification.");

        txtVerification.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                txtVerification.setText("Sending verification email...");
                txtVerification.setEnabled(false);

                mUser.sendEmailVerification()
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                if(task.isSuccessful()) txtVerification.setText("Verification email sent.");
                                else txtVerification.setText("Verification failed.");
                            }
                        });
            }
        });
    }

    private void resetEmail(){
        View view = getLayoutInflater().inflate(R.layout.prompt_user_input, null);
        final EditText etEmail = view.findViewById(R.id.et_input);
        Button btnReset = view.findViewById(R.id.btn_submit);
        etEmail.setHint("Type new email");
        btnReset.setText("Reset email");

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newEmail = etEmail.getText().toString();

                if(TextUtils.isEmpty(newEmail)){
                    Toast.makeText(MainActivity.this, "Email required!", Toast.LENGTH_SHORT).show();
                }
                else {
                    mUser.updateEmail(newEmail)
                            .addOnCompleteListener(new OnCompleteListener<Void>() {
                                @Override
                                public void onComplete(@NonNull Task<Void> task) {
                                    if (task.isSuccessful()) {
                                        Toast.makeText(MainActivity.this,
                                                "User email address updated.", Toast.LENGTH_SHORT).show();
                                    }
                                    else  {
                                        Toast.makeText(MainActivity.this,
                                                "User email address not updated.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset Email");
        builder.setView(view);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void resetPassword(){
        View view = getLayoutInflater().inflate(R.layout.prompt_user_input, null);
        final EditText etPassword = view.findViewById(R.id.et_input);
        Button btnReset = view.findViewById(R.id.btn_submit);
        etPassword.setHint("Type new password");
        btnReset.setText("Reset password");

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newPassword = etPassword.getText().toString();

                if(TextUtils.isEmpty(newPassword)){
                    Toast.makeText(MainActivity.this, "Password required!", Toast.LENGTH_SHORT).show();
                }
                else {
                    mUser.updatePassword(newPassword)
                            .addOnCompleteListener(new OnCompleteListener<Void>() {
                                @Override
                                public void onComplete(@NonNull Task<Void> task) {
                                    if (task.isSuccessful()) {
                                        Toast.makeText(MainActivity.this,
                                                "User password updated.", Toast.LENGTH_SHORT).show();
                                    }
                                    else  {
                                        Toast.makeText(MainActivity.this,
                                                "User password not updated.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset Password");
        builder.setView(view);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void sendPasswordResetEmail(){
        mAuth.sendPasswordResetEmail(mUser.getEmail())
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Toast.makeText(MainActivity.this,
                                    "Password reset email sent.", Toast.LENGTH_SHORT).show();
                        }
                        else  {
                            Toast.makeText(MainActivity.this,
                                    "Password reset email not sent.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void signOut(){
        mAuth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void deleteUser(){
        mUser.delete()
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Toast.makeText(MainActivity.this,
                                    "Account deleted successfully.", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(MainActivity.this, LoginActivity.class));
                            finish();
                        }
                        else  {
                            Toast.makeText(MainActivity.this,
                                    "Failed to delete account.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void reAuthUser(){
        View view = getLayoutInflater().inflate(R.layout.prompt_user_input, null);
        final EditText etEmail = view.findViewById(R.id.et_input);
        final EditText etPassword = view.findViewById(R.id.et_input2);
        Button btnReAuth = view.findViewById(R.id.btn_submit);
        etEmail.setHint("Type email");
        etPassword.setVisibility(View.VISIBLE);
        etPassword.setHint("Type password");
        btnReAuth.setText("Re Authenticate");

        btnReAuth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = etEmail.getText().toString();
                String password = etPassword.getText().toString();

                if(TextUtils.isEmpty(email) || TextUtils.isEmpty(password)){
                    Toast.makeText(MainActivity.this, "Email & Password required!", Toast.LENGTH_SHORT).show();
                }
                else {
                    // Get auth credentials from the user for re-authentication. The example below shows
                    // email and password credentials but there are multiple possible providers,
                    // such as GoogleAuthProvider or FacebookAuthProvider.
                    AuthCredential credential = EmailAuthProvider
                            .getCredential(email, password);

                    // Prompt the user to re-provide their sign-in credentials
                    mUser.reauthenticate(credential)
                            .addOnCompleteListener(new OnCompleteListener<Void>() {
                                @Override
                                public void onComplete(@NonNull Task<Void> task) {
                                    if (task.isSuccessful()) {
                                        Toast.makeText(MainActivity.this,
                                                "Re-authenticated.", Toast.LENGTH_SHORT).show();
                                    }
                                    else  {
                                        Toast.makeText(MainActivity.this,
                                                "Re-authentication failed.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Re-Authentication");
        builder.setView(view);
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
