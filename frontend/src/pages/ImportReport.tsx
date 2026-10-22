import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Progress,
  Result,
  Row,
  Select,
  Space,
  Steps,
  Table,
  Tag,
  Upload,
  message,
} from 'antd'
import { CloudUploadOutlined, InboxOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import http from '../api/client'

const { Dragger } = Upload

export default function ImportReport() {
  const navigate = useNavigate()
  const [step, setStep] = useState(0)
  const [preview, setPreview] = useState<any>()
  const [patients, setPatients] = useState<any[]>([])
  const [result, setResult] = useState<any>()
  const [form] = Form.useForm()

  useEffect(() => {
    http.get('/patients', { params: { page: 1, size: 100 } }).then((d: any) => {
      setPatients(d.list || [])
    }).catch(() => undefined)
  }, [])

  const upload = async (options: any) => {
    const { file, onSuccess, onError } = options
    const fd = new FormData()
    fd.append('file', file as File)
    try {
      const data: any = await http.post('/import/report', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 180000,
      })
      setPreview(data)
      const t = data.topography || {}
      const b = data.biomechanics || {}
      form.setFieldsValue({
        patientName: data.suggestedPatientName,
        gender: data.suggestedGender,
        dateOfBirth: data.suggestedDateOfBirth ? dayjs(data.suggestedDateOfBirth) : undefined,
        examDate: data.suggestedExamDate ? dayjs(data.suggestedExamDate) : dayjs(),
        eyeSide: data.suggestedEyeSide || 'right',
        frontK1: t.frontK1,
        frontK2: t.frontK2,
        frontKm: t.frontKm,
        frontKmax: t.frontKmax,
        frontRmin: t.frontRmin,
        thinnestPachy: t.thinnestPachy,
        backRmin: t.backRmin,
        ctbi: b.ctbi,
        iop: t.iop,
      })
      setStep(1)
      message.success('识别完成，请核对提取结果')
      onSuccess?.({}, file)
    } catch (e) {
      onError?.(e as any)
    }
  }

  const confirm = async () => {
    const v = await form.validateFields()
    const payload = {
      imageId: preview.imageId,
      patientId: v.patientId || undefined,
      name: v.patientName,
      gender: v.gender,
      dateOfBirth: v.dateOfBirth ? v.dateOfBirth.format('YYYY-MM-DD') : undefined,
      examDate: v.examDate ? v.examDate.format('YYYY-MM-DD') : dayjs().format('YYYY-MM-DD'),
      eyeSide: v.eyeSide,
      examType: 'Pentacam',
      notes: '报告导入',
      ocrJson: preview.ocrJson,
      topography: {
        frontK1: v.frontK1,
        frontK2: v.frontK2,
        frontKm: v.frontKm,
        frontKmax: v.frontKmax,
        frontRmin: v.frontRmin,
        thinnestPachy: v.thinnestPachy,
        backRmin: v.backRmin,
        iop: v.iop,
      },
      biomechanics: { ctbi: v.ctbi },
    }
    const data: any = await http.post('/import/confirm', payload)
    setResult(data)
    setStep(2)
    message.success('已入台账，并自动更新进展标记与随访计划')
  }

  const fieldRows = preview
    ? Object.entries(preview.fields || {}).map(([k, v]) => ({ key: k, value: v }))
    : []

  return (
    <div>
      <div className="page-header">
        <div>
          <h2 className="page-title">报告导入</h2>
          <div className="page-subtitle">
            上传角膜检查报告照片 → OCR 自动提取指标 → 医生核对 → 自动入台账并更新随访计划
          </div>
        </div>
        <Tag color={preview ? 'blue' : 'default'}>
          {preview ? `报告类型：${preview.reportType} · 置信度 ${(preview.confidence * 100).toFixed(0)}%` : '支持 Pentacam 报告'}
        </Tag>
      </div>

      <Card className="soft-card" bordered={false}>
        <Steps
          current={step}
          style={{ marginBottom: 24 }}
          items={[
            { title: '上传报告' },
            { title: '核对提取结果' },
            { title: '完成入库' },
          ]}
        />

        {step === 0 && (
          <Dragger
            accept=".jpg,.jpeg,.png,.bmp"
            showUploadList={false}
            customRequest={upload}
            style={{ padding: '28px 0', background: '#fbfdff' }}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">点击或拖拽角膜检查报告图片到此处</p>
            <p className="ant-upload-hint">
              支持 jpg / png / bmp。演示环境请使用系统生成的模拟报告图；OCR 不可用时可转为手动录入。
            </p>
          </Dragger>
        )}

        {step === 1 && preview && (
          <>
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={8}>
                <Card size="small" bordered={false} style={{ background: '#f6f9ff' }}>
                  <div className="stat-label">识别置信度</div>
                  <Progress percent={Math.round((preview.confidence || 0) * 100)} size="small" />
                </Card>
              </Col>
              <Col span={8}>
                <Card size="small" bordered={false} style={{ background: '#f6f9ff' }}>
                  <div className="stat-label">识别字段数</div>
                  <div className="stat-value" style={{ fontSize: 24 }}>{fieldRows.length}</div>
                </Card>
              </Col>
              <Col span={8}>
                <Card size="small" bordered={false} style={{ background: '#f6f9ff' }}>
                  <div className="stat-label">可疑字段（未采用）</div>
                  <div className="stat-value" style={{ fontSize: 24 }}>
                    {Object.keys(preview.rejected || {}).length}
                  </div>
                </Card>
              </Col>
            </Row>

            {Object.keys(preview.rejected || {}).length > 0 && (
              <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 16 }}
                message="部分读数超出合理区间，已被系统拒绝采用，请在下方人工核对"
                description={Object.entries(preview.rejected || {})
                  .map(([k, v]) => `${k}: ${v}`)
                  .join('；')}
              />
            )}

            <Form form={form} layout="vertical">
              <h3 style={{ fontSize: 15, margin: '8px 0 12px' }}>患者信息</h3>
              <Row gutter={12}>
                <Col span={6}>
                  <Form.Item name="patientId" label="关联已有患者（可选）">
                    <Select
                      allowClear
                      showSearch
                      placeholder="按姓名/出生日期自动匹配"
                      optionFilterProp="label"
                      options={patients.map((p) => ({
                        value: p.patientId,
                        label: `${p.name}（${p.dateOfBirth || '生日未知'}）`,
                      }))}
                    />
                  </Form.Item>
                </Col>
                <Col span={6}>
                  <Form.Item name="patientName" label="姓名" rules={[{ required: true, message: '请填写姓名' }]}>
                    <Input />
                  </Form.Item>
                </Col>
                <Col span={4}>
                  <Form.Item name="gender" label="性别">
                    <Select
                      allowClear
                      options={[
                        { value: 'Male', label: '男' },
                        { value: 'Female', label: '女' },
                      ]}
                    />
                  </Form.Item>
                </Col>
                <Col span={4}>
                  <Form.Item name="dateOfBirth" label="出生日期">
                    <DatePicker style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={4}>
                  <Form.Item name="eyeSide" label="眼别" rules={[{ required: true }]}>
                    <Select
                      options={[
                        { value: 'right', label: '右眼' },
                        { value: 'left', label: '左眼' },
                      ]}
                    />
                  </Form.Item>
                </Col>
              </Row>

              <h3 style={{ fontSize: 15, margin: '8px 0 12px' }}>检查指标（可人工修正）</h3>
              <Row gutter={12}>
                <Col span={4}>
                  <Form.Item name="examDate" label="检查日期" rules={[{ required: true }]}>
                    <DatePicker style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={4}>
                  <Form.Item name="frontKmax" label="KMax (D)" rules={[{ required: true, message: '必填' }]}>
                    <InputNumber min={30} max={90} step={0.1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={4}>
                  <Form.Item name="thinnestPachy" label="最薄点 (μm)" rules={[{ required: true, message: '必填' }]}>
                    <InputNumber min={150} max={900} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={4}>
                  <Form.Item name="frontKm" label="前表面 Km (D)">
                    <InputNumber min={30} max={90} step={0.1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={4}>
                  <Form.Item name="frontRmin" label="前表面 Rmin (mm)">
                    <InputNumber min={3} max={12} step={0.01} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={4}>
                  <Form.Item name="backRmin" label="后表面 Rmin (mm)">
                    <InputNumber min={3} max={12} step={0.01} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={4}>
                  <Form.Item name="frontK1" label="前表面 K1 (D)">
                    <InputNumber min={30} max={90} step={0.1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={4}>
                  <Form.Item name="frontK2" label="前表面 K2 (D)">
                    <InputNumber min={30} max={90} step={0.1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={4}>
                  <Form.Item name="ctbi" label="cTBI">
                    <InputNumber min={0} max={1.5} step={0.01} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={4}>
                  <Form.Item name="iop" label="眼压 (mmHg)">
                    <InputNumber min={5} max={40} step={0.1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>

              <Collapse
                ghost
                items={[
                  {
                    key: 'raw',
                    label: '查看 OCR 原始提取结果',
                    children: (
                      <Table
                        size="small"
                        rowKey="key"
                        pagination={false}
                        dataSource={fieldRows}
                        columns={[
                          { title: '字段', dataIndex: 'key', width: 220 },
                          { title: '值', dataIndex: 'value' },
                        ]}
                      />
                    ),
                  },
                ]}
              />

              <Space style={{ marginTop: 16 }}>
                <Button type="primary" onClick={confirm}>
                  确认入库
                </Button>
                <Button onClick={() => setStep(0)}>重新上传</Button>
              </Space>
            </Form>
          </>
        )}

        {step === 2 && result && (
          <Result
            status="success"
            title="已写入随访台账"
            subTitle={`患者：${result.patientName}　检查号：${result.examId}　分期：${
              result.stageCode || '-'
            }　风险：${result.riskFlag || '-'}`}
            extra={[
              <Button type="primary" key="detail" onClick={() => navigate(`/patients/${result.patientId}`)}>
                查看患者台账
              </Button>,
              <Button
                key="again"
                onClick={() => {
                  setStep(0)
                  setPreview(undefined)
                  setResult(undefined)
                  form.resetFields()
                }}
              >
                继续导入
              </Button>,
            ]}
          />
        )}
      </Card>
    </div>
  )
}
