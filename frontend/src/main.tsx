import { createRoot } from 'react-dom/client'
import './index.css'
import router from "./router";
import {RouterProvider} from "react-router/dom";
// import App from './App.tsx'
import {QueryClientProvider, QueryClient} from "@tanstack/react-query";

const queryClient = new QueryClient()

createRoot(document.getElementById('root')!).render(
    <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
    </QueryClientProvider>
)
