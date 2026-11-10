import { createHashRouter } from 'react-router'
import { appRoutes } from './routes'

// Preserve deployed #/ links, including email URLs, without server rewrite rules.
export const router = createHashRouter(appRoutes)
