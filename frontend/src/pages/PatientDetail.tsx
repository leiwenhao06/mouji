import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Divider,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd'
import { ArrowLeftOutlined, DownloadOutlined, PlusOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import dayjs from 'dayjs'
import ReactECharts from 'echarts-for-react'
import http, { downloadFile } from '../api/client'
import { Exam, FollowupPlan, Patient } from '../api/types'
import { PlanStatusTag, RiskTag, StageTag, eyeLabel } from '../components/StatusTag'
import AuthImage from '../components/AuthImage'

export default function PatientDetail() {
  const { patientId } = useParams()
  const navigate = useNavigate()
  const [patient, setPatient] = useState<Patient>()
  const [exams, setExams] = useState<Exam[]>([])
  const [plan, setPlan] = useState<FollowupPlan>()
  const [images, setImages] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm()

  const load = async () => {
    if (!patientId) return
    setLoading(true)
    try {
      const data: any = await http.get(`/patients/${patientId}/timeline`)
      setPatient(data.patient)
      setExams(data.exams || [])
      setPlan(data.plan)
      const imgs: any = await http.get(`/images/patient/${patientId}`)
      setImages(imgs || [])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [patientId])

  const chart = (field: 'frontKmax' | 'thinnestPachy', title: string, unit: string) => {
    const series = ['right', 'left'].map((eye) => ({
      name: eyeLabel(eye),
      type: 'line',
      smooth: true,
      connectNulls: true,
      symbolSize: 7,
      data: exams
        .filter((e) => e.eyeSide === eye && e.topography && (e.topography as any)[field] != null)
        .map((e) => [e.examDate, (e.topography as any)[field]]),
    }))
    return {
      title: { text: title, left: 8, top: 4, textStyle: { fontSize: 14, color: '#102a43' } },
      tooltip: { trigger: 'axis', valueFormatter: (v: number) => `${v}${unit}` },
      legend: { top: 4, right: 12 },
      grid: { left: 46, right: 20, top: 46, bottom: 36 },
      xAxis: { type: 'time', axisLabel: { formatter: '{MM}-{dd}' } },
      yAxis: { type: 'value', scale: true, axisLabel: { formatter: `{value}${unit}` } },
      series,
    }
  }

  const addExam = async () => {
    const v = await form.validateFields()
    const topography = {
      frontK1: v.frontK1,
      frontK2: v.frontK2,
      frontKm: v.frontKm,
      frontKmax: v.frontKmax,
      frontRmin: v.frontRmin,
      thinnestPachy: v.thinnestPachy,
      backRmin: v.backRmin,
      iop: v.iop,
    }
    const biomechanics = { ctbi: v.ctbi }
    const created: any = await http.post('/exams', {
      patientId,
      examDate: v.examDate.format('YYYY-MM-DD'),
      eyeSide: v.eyeSide,
      examType: 'Pentacam',
      notes: v.notes,
      topography,
      biomechanics,
    })
    message.success(
      `检查已录入，系统判定：${created.stageCode || '-'} / 风险 ${
        created.riskFlag === 'PROGRESSION' ? '进展' : created.riskFlag
      }`,
    )
    setOpen(false)
    form.resetFields()
    load()
  }

  const columns = [
    { title: '检查日期', dataIndex: 'examDate', width: 120 },
    { title: '眼别', dataIndex: 'eyeSide', width: 80, render: (v: string) => eyeLabel(v) },
    {
      title: 'KMax (D)',
      width: 110,
      render: (_: any, r: Exam) => r.topography?.frontKmax ?? '-',
    },
    {
      title: '最薄点 (μm)',
      width: 120,
      render: (_: any, r: Exam) => r.topography?.thinnestPachy ?? '-',
    },
    {
      title: '后表面Rmin (mm)',
      width: 140,
      render: (_: any, r: Exam) => r.topography?.backRmin ?? '-',
    },
    {
      title: 'cTBI',
      width: 90,
      render: (_: any, r: Exam) => r.biomechanics?.ctbi ?? '-',
    },
    {
      title: '分期',
      dataIndex: 'stageCode',
      width: 110,
      render: (v: string) => <StageTag value={v} />,
    },
    {
      title: '风险',
      dataIndex: 'riskFlag',
      width: 100,
      render: (v: string) => <RiskTag value={v} />,
    },
    {
      title: '报告图',
      width: 110,
      render: (_: any, r: Exam) => {
        const img = images.find((i) => i.examId === r.examId)
        return img ? <AuthImage imageId={img.imageId} width={88} height={62} /> : '-'
      },
    },
  ]

  const latest = useMemo(() => (exams.length ? exams[exams.length - 1] : undefined), [exams])
  const previous = useMemo(() => {
    if (!latest) return undefined
    return [...exams]
      .reverse()
      .find((e) => e.examId !== latest.examId && e.eyeSide === latest.eyeSide && e.topography)
  }, [exams, latest])

  const kmax = latest?.topography?.frontKmax
  const kmaxDelta =
    kmax != null && previous?.topography?.frontKmax != null
      ? Number(kmax) - Number(previous.topography.frontKmax)
      : undefined
  const thin = latest?.topography?.thinnestPachy
  const thinDelta =
    thin != null && previous?.topography?.thinnestPachy != null
      ? Number(thin) - Number(previous.topography.thinnestPachy)
      : undefined
  const overdue = plan?.status === 'OVERDUE'

  const kmaxDeltaText =
    kmaxDelta == null
      ? previous
        ? '暂无可比数据'
        : '首次检查'
      : `较上次 ${kmaxDelta > 0 ? '+' : ''}${kmaxDelta.toFixed(2)} D`
  const kmaxDeltaClass =
    kmaxDelta == null ? 'flat' : kmaxDelta > 0.05 ? 'up' : kmaxDelta < -0.05 ? 'down' : 'flat'
  const thinDeltaText =
    thinDelta == null
      ? previous
        ? '暂无可比数据'
        : '首次检查'
      : `较上次 ${thinDelta > 0 ? '+' : ''}${thinDelta.toFixed(0)} μm`
  // 厚度下降代表病情进展，用红色提示
  const thinDeltaClass =
    thinDelta == null ? 'flat' : thinDelta < -1 ? 'up' : thinDelta > 1 ? 'down' : 'flat'

  return (
    <div>
      <div className="page-header" style={{ marginBottom: 12 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/patients')}>
          返回患者台账
        </Button>
      </div>

      <Card className="soft-card patient-hero" bordered={false} loading={loading} style={{ marginBottom: 16 }}>
        <div className="patient-big-avatar">{(patient?.name || '患').slice(0, 1)}</div>
        <div className="patient-hero-main">
          <div className="patient-hero-name">{patient?.name || '患者'}</div>
          <Space size={6} wrap>
            <Tag>{patient?.gender === 'Male' ? '男' : patient?.gender === 'Female' ? '女' : '性别未填'}</Tag>
            {patient?.age != null && <Tag>{patient.age} 岁</Tag>}
            {latest && <StageTag value={latest.stageCode} />}
            {latest && <RiskTag value={latest.riskFlag} />}
            {patient?.medicalHistory && <Tag color="default">既往史：{patient.medicalHistory}</Tag>}
          </Space>
          <div className="patient-hero-meta">
            患者ID <span className="mono">{patientId}</span> · 联系电话 {patient?.contact || '未填写'} · 检查{' '}
            {exams.length} 次 · 最近检查 {latest?.examDate || '-'}
          </div>
        </div>
        <div className="patient-hero-actions">
          <Button
            icon={<DownloadOutlined />}
            onClick={async () => {
              await downloadFile(`/export/patients/${patientId}/ledger`, '随访台账.csv')
              message.success('检查台账已导出')
            }}
          >
            导出台账
          </Button>
          <Button onClick={() => navigate(`/suggestions?patientId=${patientId}`)}>生成 AI 随访建议</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>
            录入检查
          </Button>
        </div>
      </Card>

      {overdue && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="该患者随访已逾期"
          description={`原计划随访日期：${plan?.nextDueDate}，请尽快联系患者安排复查。`}
        />
      )}

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <Card
            className="soft-card"
            bordered={false}
            loading={loading}
            title="当前随访计划"
            extra={<PlanStatusTag value={plan?.status} />}
            style={{ height: '100%' }}
          >
            {plan ? (
              <Space direction="vertical" size={6} style={{ width: '100%' }}>
                <div>
                  建议频率：<strong>每 {plan.frequencyMonths} 个月</strong>
                </div>
                <div>
                  下次随访：<strong>{plan.nextDueDate || '-'}</strong>
                </div>
                <div style={{ color: '#7a8ba0', fontSize: 12, lineHeight: 1.6 }}>
                  依据：{plan.basis}
                </div>
              </Space>
            ) : (
              <Empty description="暂无随访计划" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            )}
          </Card>
        </Col>
        <Col span={8}>
          <Card className="soft-card metric-card" bordered={false} loading={loading} style={{ height: '100%' }}>
            <div className="metric-title">最新 KMax（{latest?.eyeSide === 'left' ? '左眼' : '右眼'}）</div>
            <div className="metric-value">
              {kmax != null ? Number(kmax).toFixed(2) : '-'}
              <span className="metric-unit">D</span>
            </div>
            <div className={`metric-delta ${kmaxDeltaClass}`}>{kmaxDeltaText}</div>
          </Card>
        </Col>
        <Col span={8}>
          <Card className="soft-card metric-card" bordered={false} loading={loading} style={{ height: '100%' }}>
            <div className="metric-title">最新最薄点厚度（{latest?.eyeSide === 'left' ? '左眼' : '右眼'}）</div>
            <div className="metric-value">
              {thin != null ? Number(thin).toFixed(0) : '-'}
              <span className="metric-unit">μm</span>
            </div>
            <div className={`metric-delta ${thinDeltaClass}`}>{thinDeltaText}</div>
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <Card className="soft-card chart-card" bordered={false}>
            <ReactECharts option={chart('frontKmax', 'KMax 变化趋势', 'D')} style={{ height: 260 }} />
          </Card>
        </Col>
        <Col span={12}>
          <Card className="soft-card chart-card" bordered={false}>
            <ReactECharts option={chart('thinnestPachy', '最薄点厚度变化', 'μm')} style={{ height: 260 }} />
          </Card>
        </Col>
      </Row>

      <Card
        className="soft-card"
        bordered={false}
        title="检查台账"
        extra={<span className="page-subtitle">最近一次检查自动触发进展检测与随访计划调整</span>}
      >
        <Table
          rowKey="examId"
          loading={loading}
          columns={columns as any}
          dataSource={exams}
          pagination={{ pageSize: 8 }}
          expandable={{
            expandedRowRender: (r: Exam) => (
              <Descriptions size="small" column={4}>
                <Descriptions.Item label="前表面 K1/K2/Km">
                  {r.topography?.frontK1 ?? '-'} / {r.topography?.frontK2 ?? '-'} / {r.topography?.frontKm ?? '-'}
                </Descriptions.Item>
                <Descriptions.Item label="前表面散光">{r.topography?.frontAstig ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="前表面 QS">{r.topography?.frontQs || '-'}</Descriptions.Item>
                <Descriptions.Item label="角膜容积">{r.topography?.corneaVolume ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="cCBI / cTBI">
                  {r.biomechanics?.ccbi ?? '-'} / {r.biomechanics?.ctbi ?? '-'}
                </Descriptions.Item>
                <Descriptions.Item label="眼压">{r.topography?.iop ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="备注" span={2}>
                  {r.notes || '-'}
                </Descriptions.Item>
              </Descriptions>
            ),
          }}
        />
      </Card>

      <Divider />
      <div className="page-subtitle" style={{ textAlign: 'center' }}>
        本台账仅用于随访管理演示，所有数据为程序生成的模拟数据，不构成诊断或治疗意见。
      </div>

      <Modal
        title="录入一次角膜检查"
        open={open}
        width={640}
        onOk={addExam}
        onCancel={() => setOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 12 }}>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="examDate" label="检查日期" rules={[{ required: true, message: '请选择日期' }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="eyeSide" label="眼别" rules={[{ required: true, message: '请选择眼别' }]}>
                <Select
                  options={[
                    { value: 'right', label: '右眼' },
                    { value: 'left', label: '左眼' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="notes" label="备注">
                <Input placeholder="如：交联术后 6 个月" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="frontKmax" label="KMax (D)" rules={[{ required: true, message: 'KMax 必填' }]}>
                <InputNumber min={30} max={90} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="frontKm" label="前表面 Km (D)">
                <InputNumber min={30} max={90} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="frontRmin" label="前表面 Rmin (mm)">
                <InputNumber min={3} max={12} step={0.01} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="thinnestPachy"
                label="最薄点厚度 (μm)"
                rules={[{ required: true, message: '厚度必填' }]}
              >
                <InputNumber min={150} max={900} step={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="backRmin" label="后表面 Rmin (mm)">
                <InputNumber min={3} max={12} step={0.01} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="ctbi" label="cTBI">
                <InputNumber min={0} max={1.5} step={0.01} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="frontK1" label="前表面 K1 (D)">
                <InputNumber min={30} max={90} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="frontK2" label="前表面 K2 (D)">
                <InputNumber min={30} max={90} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="iop" label="眼压 (mmHg)">
                <InputNumber min={5} max={40} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Alert
            type="info"
            showIcon
            message="保存后系统会自动计算进展标记，并按分期/进展调整随访计划。"
          />
        </Form>
      </Modal>
    </div>
  )
}
