package com.example.ososlmtd

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.tylersuehr.esr.ContentItemLoadingStateFactory
import com.tylersuehr.esr.EmptyStateRecyclerView
import com.tylersuehr.esr.TextStateDisplay
import dmax.dialog.SpotsDialog
import gun0912.tedbottompicker.TedBottomPicker
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    internal lateinit var dbReference: DatabaseReference
    internal lateinit var title_list: MutableList<DataSnapshot>
    internal lateinit var recyclerView: RecyclerView
    private lateinit var valueEventListener: ValueEventListener
    private lateinit var query: DatabaseReference
    private lateinit var emptyRc: EmptyStateRecyclerView
    private lateinit var dbStorage: StorageReference
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var loadingDialog:android.app.AlertDialog
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView=findViewById(R.id.outer_rc)
        fab=findViewById(R.id.fab)
        dbReference=FirebaseDatabase.getInstance().reference
        dbStorage=FirebaseStorage.getInstance().reference
        recyclerView.layoutManager=LinearLayoutManager(this@MainActivity)
        emptyRc=findViewById(R.id.empty_recycler_view)
        initializeNoDataEmptyStateRecyclerView(this@MainActivity,emptyRc)
        emptyRc.invokeState(EmptyStateRecyclerView.STATE_LOADING)
        loadingDialog= SpotsDialog.Builder().setContext(this@MainActivity).setMessage(
           R.string.loading).setTheme(R.style.LoadingCustom).build()

        loadData()

        fab.setOnClickListener {
            var dialog =
                MaterialDialog(this@MainActivity).customView(
                    R.layout.dialog_view,
                    scrollable = true
                )
            dialog.title(R.string.title)
            val customView=dialog.getCustomView();
            var titleText=customView.findViewById<TextInputLayout>(R.id.titleInput).editText
            dialog.positiveButton(R.string.submit) {
                when{
                    titleText!!.text.isEmpty() ->{
                        Toast.makeText(this@MainActivity,"Enter title",
                            Toast.LENGTH_LONG).show()
                    }else->{
                    loadingDialog.show()
                        val key=dbReference.push().key
                        val data=title(titleText.text.toString())
                        dbReference.child("osos").child(key!!).setValue(data).addOnSuccessListener {
                            Toast.makeText(this@MainActivity,"Successfully inserted",
                                Toast.LENGTH_LONG).show()
                            loadingDialog.dismiss()
                            dialog.dismiss()
                        }.addOnFailureListener {
                            Toast.makeText(this@MainActivity,it.message,
                                Toast.LENGTH_LONG).show()
                            loadingDialog.dismiss()
                        }
                    }
                }
            }

            dialog.negativeButton(R.string.cancel){
                it.dismiss()
            }
            dialog.cornerRadius(16f)
            dialog.noAutoDismiss()
            dialog.cancelOnTouchOutside(false)
            dialog.show()
        }

    }

    private fun initializeNoDataEmptyStateRecyclerView(context: Context, emptyRc:EmptyStateRecyclerView) {
        emptyRc.setStateDisplay(EmptyStateRecyclerView.STATE_LOADING,
            ContentItemLoadingStateFactory.newCardLoadingState(context))
        emptyRc.setStateDisplay(EmptyStateRecyclerView.STATE_EMPTY,
            TextStateDisplay(context,"No data to display","Displays when data is available :)")
        )
    }

    fun initializeErrorEmptyStateRecyclerView(context: Context, emptyRc:EmptyStateRecyclerView, error:String) {
        emptyRc.setStateDisplay(EmptyStateRecyclerView.STATE_ERROR,
            TextStateDisplay(context,"ERROR...!","$error :(")
        )
    }

    private fun loadData() {
        dbReference.child("osos").addValueEventListener(object :ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {

            }

            override fun onDataChange(p0: DataSnapshot) {
                when{
                    p0.exists()->{
                        loadingDialog.show()
                        title_list= mutableListOf()
                        for (v in p0.children){
                            title_list.add(v)
                        }
                        recyclerView.adapter=CustomTitleView(title_list,this@MainActivity,dbReference,dbStorage,loadingDialog)
                        emptyRc.clearStateDisplays()
                        loadingDialog.dismiss()
                    }else->{
                        emptyRc.invokeState(EmptyStateRecyclerView.STATE_EMPTY)
                    }
                }
            }

        })
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when {
//            grantResults[0] == 0 -> selectImages()
            grantResults[0] == -1 -> Toast.makeText(
                this@MainActivity,
                "Please provide permissions",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    class CustomTitleView(
        var title_list: MutableList<DataSnapshot>,
        var context: Context,
        val dbReference: DatabaseReference,
        val dbStorage: StorageReference,
        val loadingDialog: AlertDialog
    ):RecyclerView.Adapter<CustomTitleView.BlockViewHolder>(){
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockViewHolder {
            return BlockViewHolder(LayoutInflater.from(context).inflate(R.layout.custom_outer_view,parent,false))
        }

        override fun getItemCount(): Int {
            return title_list.size
        }

        override fun onBindViewHolder(holder: BlockViewHolder, position: Int) {
            var data=title_list.elementAt(position).getValue(title::class.java)!!
            holder.title.text = data.title
            val key=title_list.elementAt(position).key

            holder.innerRc.layoutManager=GridLayoutManager(context,4,RecyclerView.VERTICAL,false)
            dbReference.child("osos").child(key!!).child("photo_links").addValueEventListener(object :ValueEventListener{
                override fun onCancelled(error: DatabaseError) {
                    TODO("Not yet implemented")
                }

                override fun onDataChange(snapshot: DataSnapshot) {
                    if(snapshot.exists()){
                        val photoList= mutableListOf<String>()
                        for(v in snapshot.children){
                            val data=v.getValue(String::class.java)
                            photoList.add(data!!)
                        }
                        holder.innerRc.adapter=innerViewRc(context,photoList)
                    }
                }

            })

            holder.imageBtn.setOnClickListener {
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    selectImages(key)
                } else {
                    ActivityCompat.requestPermissions(
                        context as Activity,
                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE,android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        10
                    )
                }
            }
        }

        fun selectImages(key: String?) {
            var uri_list = mutableListOf<Uri>()
            var url_list = mutableListOf<String>()
            TedBottomPicker.with(context as FragmentActivity?)
                .setPeekHeight(1600)
                .showTitle(false)
                .setCompleteButtonText("Done")
                .setEmptySelectionText("No Select")
                .setSelectedUriList(uri_list)
//                .setSelectMinCount(1)
//                .setSelectMaxCount(8)
                .showMultiImage {
                    loadingDialog.show()
                    uri_list=it
                    for ((i, v) in uri_list.withIndex()) {
                        val local_sref= dbStorage.child("osos").child(key!!).child("photo_link_$i")
                        local_sref.putFile(v)
                            .addOnSuccessListener {
                                local_sref.downloadUrl.addOnSuccessListener {
                                    url_list.add(it.toString())
                                    when{
                                        //insert photo links after storing last item in firebase storage
                                        (uri_list.size-1) ==i ->{

                                            var map= mutableMapOf<String,Any>()
                                            map["photo_links"]=url_list

                                            //update_not_used photo links urls to firebase
                                            dbReference.child("osos").child(key).updateChildren(map)
                                                .addOnSuccessListener {
                                                    loadingDialog.dismiss()
//                                                    resetFields()
                                                    Toast.makeText(
                                                        context,
                                                        "Successfull inserted images",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                                .addOnFailureListener {
                                                    loadingDialog.dismiss()
                                                    Log.w(
                                                        "Failed:",
                                                        "${it.message}"
                                                    )
                                                }

                                        }
                                    }
                                }

                            }
                    }
                }
        }


        inner class BlockViewHolder(itemView: View):RecyclerView.ViewHolder(itemView) {
            var title=itemView.findViewById<TextView>(R.id.titleTv)
            var imageBtn=itemView.findViewById<ImageView>(R.id.imageView)
            var innerRc=itemView.findViewById<RecyclerView>(R.id.innerRc)
        }

    }

    class innerViewRc(
        val context: Context,
        val imageList: MutableList<String>

    ) : RecyclerView.Adapter<innerViewRc.flatNoViewHolder>(){

        override fun onCreateViewHolder(p0: ViewGroup, p1: Int): flatNoViewHolder {
            val view = LayoutInflater.from(p0.context).inflate(R.layout.inner_view, p0, false)

            return flatNoViewHolder(view)
        }

        override fun getItemCount(): Int {
            return imageList.size
        }

        override fun onBindViewHolder(p0: flatNoViewHolder, p1: Int) {

            Glide.with(context).load(imageList[p1]).into(p0.image)
        }

        inner class flatNoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val image=itemView.findViewById<ImageView>(R.id.inner_image)
        }

    }



}