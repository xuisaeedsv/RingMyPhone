package com.example.ringmyphone

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ringmyphone.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val devices: MutableList<Device> = mutableListOf()
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root)

    fun addOrUpdate(device: Device) {
        val existingIndex = devices.indexOfFirst { it.name == device.name }
        if (existingIndex >= 0) {
            devices[existingIndex] = device
            notifyItemChanged(existingIndex)
        } else {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }

    fun removeByName(name: String) {
        val index = devices.indexOfFirst { it.name == name }
        if (index >= 0) {
            devices.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun getDevices(): List<Device> = devices

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.binding.tvDeviceName.text = device.name
        holder.binding.tvDeviceAddress.text = "${device.host}:${device.port}"
    }

    override fun getItemCount(): Int = devices.size
}
