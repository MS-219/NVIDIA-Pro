const readResponsePayload = async (response) => {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch (error) {
    return { msg: text }
  }
}

export const uploadImageFile = async (file) => {
  const formData = new FormData()
  formData.append('file', file)

  const token = localStorage.getItem('orin_admin_token') || ''
  const response = await fetch('/api/upload/image', {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData
  })
  const payload = await readResponsePayload(response)

  if (!response.ok || payload?.code !== 200 || !payload?.data?.url) {
    const error = new Error(payload?.msg || `图片上传失败（HTTP ${response.status}）`)
    error.status = response.status
    error.payload = payload
    throw error
  }
  return payload
}
