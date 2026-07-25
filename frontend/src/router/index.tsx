import {createBrowserRouter, createRoutesFromElements, Route} from "react-router";
import Login from "../pages/Login";
import Register from "../pages/Register";
// import AuthLayout from "../components/Auth/AuthLayout.tsx";
const routeElements = createRoutesFromElements(
    <Route path={'/'} element={<Login />}>
        <Route path={'/login'} element={<Login />} />
        <Route path={'/register'} element={<Register />} />
    </Route>
)
const router = createBrowserRouter(routeElements)
export default router;