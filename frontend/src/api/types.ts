export interface Patient {
  patientId: string
  name: string
  gender?: string
  dateOfBirth?: string
  age?: number
  contact?: string
  medicalHistory?: string
  createdAt?: string
}

export interface Topography {
  frontK1?: number
  frontK2?: number
  frontKm?: number
  frontKmax?: number
  frontRmin?: number
  frontQVal?: number
  frontAxis?: number
  frontAstig?: number
  frontQs?: string
  backK1?: number
  backK2?: number
  backKm?: number
  backRmin?: number
  backQVal?: number
  backQs?: string
  pupilCenterPachy?: number
  pachyApex?: number
  thinnestPachy?: number
  corneaVolume?: number
  acDepth?: number
  iop?: number
}

export interface Biomechanics {
  ccbi?: number
  ctbi?: number
  isValue?: number
  spA1?: number
  integrRadius?: number
  arth?: number
  daRatio?: number
  ssi?: number
}

export interface Exam {
  examId: number
  patientId: string
  examDate: string
  examTime?: string
  eyeSide: string
  examType: string
  notes?: string
  riskFlag: string
  stageCode?: string
  topography?: Topography
  biomechanics?: Biomechanics
  imagePath?: string
}

export interface FollowupPlan {
  planId: number
  patientId: string
  patientName?: string
  stageCode?: string
  frequencyMonths: number
  lastExamDate?: string
  nextDueDate?: string
  status: string
  basis?: string
  riskFlag?: string
}

export interface Suggestion {
  suggestionId: number
  requestId: string
  patientId?: string
  patientName?: string
  status: string
  engine?: string
  summary?: string
  resultJson?: string
  disclaimer?: string
  errorMessage?: string
  requestedAt?: string
  durationMs?: number
  adopted?: boolean | null
}
