package com.example.elderus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class HospitalGuardianFragment : Fragment(), OnMapReadyCallback {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null

    private lateinit var CallButton : AppCompatButton

    private var selectedPhoneNumber: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
    }

    override fun onCreateView(

        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_hospital, container, false)


    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync { googleMap ->
            this.googleMap = googleMap
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                googleMap.isMyLocationEnabled = true
                getCurrentLocation()
            } else {
                ActivityCompat.requestPermissions(requireActivity(),
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1)
            }

            CallButton = view.findViewById(R.id.btn_call)

            // CallButton 클릭 이벤트 설정
            CallButton.setOnClickListener {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    // 권한이 없는 경우 권한을 요청
                    ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.CALL_PHONE),
                        REQUEST_CALL_PERMISSION
                    )
                } else {
                    // 권한이 있는 경우 전화 걸기
                    selectedPhoneNumber?.let { makePhoneCall(it) }
                }
            }


            // 마커 클릭 이벤트 핸들러 설정
            googleMap.setOnMarkerClickListener { marker ->
                // 병원 정보 가져오기
                val hospitalInfo = marker?.tag as? HospitalInfo
                hospitalInfo?.let { info ->
                    // 레이아웃 찾기
                    val layout = view.findViewById<ConstraintLayout>(R.id.hospitalInfo)
                    // 병원 정보 설정
                    val tvHospitalName = layout.findViewById<TextView>(R.id.tvHospitalName)
                    val tvHospitalAddress = layout.findViewById<TextView>(R.id.tvHospitalAddress)

                    val callButton = layout.findViewById<TextView>(R.id.btn_call)

                    selectedPhoneNumber = info.phoneNumber

                    tvHospitalName.text = info.name
                    tvHospitalAddress.text = info.address
                    // 레이아웃 보이게 하기
                    layout.visibility = View.VISIBLE
                }

                true // 마커 클릭 이벤트 처리 완료
            }



        }
    }


    // 전화 걸기
    private fun makePhoneCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL)
        intent.data = Uri.parse("tel:$phoneNumber")
        startActivity(intent)
    }



    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // 위치 권한이 없을 때 권한 요청
            ActivityCompat.requestPermissions(requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1)
        } else {
            // 위치 권한이 있을 때 위치 정보 가져오기
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    location?.let {
                        Log.d("현재 위치: ", "Current location: latitude = ${it.latitude}, longitude = ${it.longitude}")
                        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f)) // 15f는 줌 레벨입니다. 이 값을 조정하여 카메라의 줌 레벨을 변경할 수 있습니다.
                        searchNearbyHospitals(it.latitude, it.longitude)
                    } ?: Log.e("현재 위치: ", "Failed to get location.")
                }
        }
    }





    private fun searchNearbyHospitals(latitude: Double, longitude: Double) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(PlacesApiService::class.java)

        val request = SearchRequest(
            includedTypes = listOf("hospital"),
            maxResultCount = 10,
            locationRestriction = LocationRestriction(
                circle = Circle(
                    center = Center(
                        latitude = latitude,
                        longitude = longitude
                    ),
                    radius = 50000.0
                )
            )
        )

        Log.d("@@HospitalFragment", "Sending request to API: $request")

        val call = service.searchNearby(request)
        call.enqueue(object : Callback<SearchResponse> {
            override fun onResponse(call: Call<SearchResponse>, response: Response<SearchResponse>) {
                if (response.isSuccessful) {
                    val places = response.body()?.places ?: emptyList()
                    Log.d("@@HospitalFragment", "Number of hospitals found: ${places.size}")

                    places.forEach { place ->
                        place.location?.let { location ->
                            // 위치 정보를 로그로 출력
                            Log.d("@@HospitalFragment", "Found place: ${place.displayName.text}, location: $location")

                            // 병원 정보를 생성
                            val hospitalInfo = HospitalInfo(
                                name = place.displayName.text,
                                position = LatLng(location.latitude, location.longitude),
                                address = place.address,
                                phoneNumber = place.phoneNumber
                            )

                            // 병원의 위치를 마커로 표시하고, 마커의 tag에 병원 정보를 저장
                            val marker = googleMap?.addMarker(MarkerOptions().position(hospitalInfo.position).title(hospitalInfo.name))
                            marker?.tag = hospitalInfo
                        } ?: Log.e("@@HospitalFragment", "Location is null for place: ${place.displayName.text}")
                    }
                } else {
                    // API 응답 실패를 로그로 출력
                    Log.e("@@HospitalFragment", "API call failed with response code: ${response.code()}, message: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<SearchResponse>, t: Throwable) {
                // API 호출 실패를 로그로 출력
                Log.e("@@HospitalFragment", "API call failed with error: ${t.message}")
            }
        })
    }

    companion object {
        const val REQUEST_CALL_PERMISSION = 1
    }
}


