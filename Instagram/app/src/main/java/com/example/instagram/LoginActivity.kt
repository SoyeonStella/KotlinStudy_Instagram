package com.example.instagram

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.android.synthetic.main.activity_login.*
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*


class LoginActivity : AppCompatActivity() {

    var auth: FirebaseAuth? = null
    var googleSignInClient : GoogleSignInClient? = null
    var GOOGLE_LOGIN_CODE = 9001 //구글 로그인 시 사용할 리퀘스트 값
    var callbackManager : CallbackManager? = null //facebook 로그인 결과를 가져오는 callback Manager 만들기

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        //firebase 로그인 통합 관리하는 객체 만들기
        auth = FirebaseAuth.getInstance()

        email_login_button.setOnClickListener {
             signinAndSignup()
        }

        google_sign_in_button.setOnClickListener {
            //google login first step
            googleLogin()
        }

        facebook_login_button.setOnClickListener {
            //facebook login first step
            facebookLogin()
        }

        //구글 로그인 옵션 만들기
        var gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // 구글 API 키
            .requestEmail() // email id 받아오기
            .build() // build로 닫아줍니다

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        callbackManager = CallbackManager.Factory.create() //callbackManager를 만들어 넣어

        //printHashKey()
    }
    //HashKey값
    //YYGuN6DqYJHf5GNl5XyQJCEBx9I=

    //hash값 받아오는 함수
    fun printHashKey() {
        try {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            for (signature in info.signatures) {
                val md: MessageDigest = MessageDigest.getInstance("SHA")
                md.update(signature.toByteArray())
                val hashKey = String(Base64.encode(md.digest(), 0))
                Log.i("TAG", "printHashKey() Hash Key: $hashKey")
            }
        } catch (e: NoSuchAlgorithmException) {
            Log.e("TAG", "printHashKey()", e)
        } catch (e: Exception) {
            Log.e("TAG", "printHashKey()", e)
        }
    }

    fun googleLogin() {
        var signInIntent = googleSignInClient?.signInIntent
        startActivityForResult(signInIntent, GOOGLE_LOGIN_CODE)
    }

    fun facebookLogin() {
        //facebook에서 받을 권한(여기서는 profile과 email) 요청하기
        LoginManager.getInstance()
            .logInWithReadPermissions(this, Arrays.asList("public_profile", "email"))

        //registerCallback()에서 callbackManager 등록해 주기
        LoginManager.getInstance()
            .registerCallback(callbackManager, object : FacebookCallback<LoginResult>{
                override fun onSuccess(result: LoginResult?) {
                    //second step
                    //로그인이 성공하면 페이스북 데이터를 파이어베이스로 넘겨준다
                    handleFacebookAccessToken(result?.accessToken)
                }

                override fun onCancel() {

                }

                override fun onError(error: FacebookException?) {

                }
                //facebook 로그인이 성공했을 시 넘어오는 부분

            })
    }

    fun handleFacebookAccessToken(token : AccessToken? ) {
        //token id를 credential로 넘겨주기
        var credential = FacebookAuthProvider.getCredential(token?.token!!)
        //credential을 파이어베이스로 넘겨주기
        auth?.signInWithCredential(credential)
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    //third step
                    //로그인 성공(id, pw 일치)
                    moveMainPage(task.result?.user)
                } else {
                    //로그인 실패
                    Toast.makeText(this, task.exception?.message, Toast.LENGTH_LONG).show()
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        /*
        *
        * 페이스북 로그인 부분
        *
        * */
        //result안에 callbackManager를 넘겨주기
        callbackManager?.onActivityResult(requestCode, resultCode, data)

        /*
        *
        * 구글 로그인 부분
        *
        * */
        if(requestCode == GOOGLE_LOGIN_CODE) {
            //구글에서 넘겨주는 로그인 결과값 받아오기
            var result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
            if(result.isSuccess) { //로그인 성공 시
                //이 값을 파이어베이스에 넘길수 있도록 만들어 주기
                var account = result.signInAccount
                //second step
                firebaseAuthWithGoogle(account)
            } else {
                Toast.makeText(this, "로그인 실패", Toast.LENGTH_LONG).show()
            }
        }
    }

    //구글 로그인 성공시 토큰값을 파이어베이스로 넘겨주어서 계정을 생성하는 코드
    fun firebaseAuthWithGoogle(account : GoogleSignInAccount?) {
        //account 안에 있는 token id를 넘겨주기
        var credential = GoogleAuthProvider.getCredential(account?.idToken, null)
        auth?.signInWithCredential(credential)
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    //로그인 성공(id, pw 일치)
                    moveMainPage(task.result?.user)
                } else {
                    //로그인 실패
                    Toast.makeText(this, task.exception?.message, Toast.LENGTH_LONG).show()
                }
            }
    }

    fun signinAndSignup() {
        auth?.createUserWithEmailAndPassword(email_edittext.text.toString(), password_edittext.text.toString())
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    //아이디 생성 성공 시
                    moveMainPage(task.result?.user)
                } else if(task.exception?.message.isNullOrEmpty()) {
                    Toast.makeText(this,task.exception?.message, Toast.LENGTH_LONG).show()
                } else {
                    //이미 계정이 있는 경우
                    signinEmail()
                }
            }

    }

    //로그인 함수
    fun signinEmail() {
        auth?.signInWithEmailAndPassword(email_edittext.text.toString(), password_edittext.text.toString())
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    //로그인 성공(id, pw 일치)
                    moveMainPage(task.result?.user)
                } else {
                    //로그인 실패
                    Toast.makeText(this, task.exception?.message, Toast.LENGTH_LONG).show()
                }
            }
    }

    //로그인 성공 시 다음 페이지로 넘어가는 함수
    fun moveMainPage(user: FirebaseUser?) { //firebaseUser상태를 넘겨줌
        if(user != null) {//user가 있을 경우
            startActivity(Intent(this,MainActivity::class.java))
        }
    }
}

